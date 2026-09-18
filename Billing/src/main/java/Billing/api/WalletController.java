package Billing.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import Billing.api.dto.BillingRecordResponse;
import Billing.api.dto.BillingSummaryResponse;
import Billing.api.dto.PlantSpendResponse;
import Billing.api.dto.TransactionResponse;
import Billing.api.dto.UnlocksResponse;
import Billing.api.dto.WalletResponse;
import Billing.billing.PlantPricing;
import Billing.billing.PlantType;
import Billing.billing.PlantTypeLockedException;
import Billing.billing.StoragePricing;
import Billing.billing.UnlockService;
import Billing.billing.UnlockThresholds;
import Billing.billing.WalletSpendingService;
import Billing.model.BillingRecordRepository;
import Billing.model.TransactionType;
import Billing.model.WalletRepository;
import Billing.model.WalletTransactionRepository;
import jakarta.validation.Valid;

/**
 * Read-only: wallets, billing history, and transaction history. No business logic here -- every
 * method is a straight repository read mapped to a response DTO, the same shape as
 * {@code Producer.api.PowerPlantController}'s own {@code list}/{@code get} methods, so there is no
 * intermediate service layer for pure reads with nothing to compute.
 */
@RestController
@RequestMapping("/api/billing")
public class WalletController {

    private static final int DEFAULT_LIMIT = 50;

    /**
     * A plant is not owned by any zone -- it serves the whole grid -- so building or growing one
     * is charged against this single shared wallet rather than a zone's own. It is stored as just
     * another row in the same {@code wallet} table, lazily created the same way a zone's wallet is;
     * it never receives a {@code BILL_DEBIT} because {@code BillingCycleService} only ever writes
     * to the real zone ids it reads off {@code customer.demand}.
     */
    public static final String GRID_WALLET_ID = "GRID";
    public static final String GRID_WALLET_NAME = "Grid Treasury";

    private final WalletRepository wallets;
    private final BillingRecordRepository billingRecords;
    private final WalletTransactionRepository transactions;
    private final WalletSpendingService spending;
    private final UnlockService unlocks;
    private final double startingBalance;
    private final double decommissionRefundRatio;

    public WalletController(WalletRepository wallets, BillingRecordRepository billingRecords,
            WalletTransactionRepository transactions, WalletSpendingService spending, UnlockService unlocks,
            @Value("${billing.starting-balance}") double startingBalance,
            @Value("${billing.decommission-refund-ratio}") double decommissionRefundRatio) {
        this.wallets = wallets;
        this.billingRecords = billingRecords;
        this.transactions = transactions;
        this.spending = spending;
        this.unlocks = unlocks;
        this.startingBalance = startingBalance;
        this.decommissionRefundRatio = decommissionRefundRatio;
    }

    /** Every zone's current wallet -- the dashboard's summary view. */
    @GetMapping("/wallets")
    public List<WalletResponse> wallets() {
        return wallets.findAll().stream().map(WalletResponse::from).toList();
    }

    @GetMapping("/zones/{zoneId}/wallet")
    public WalletResponse wallet(@PathVariable String zoneId) {
        return wallets.findById(zoneId)
                .map(WalletResponse::from)
                .orElseThrow(() -> new ZoneNotBilledException(zoneId));
    }

    @GetMapping("/zones/{zoneId}/history")
    public List<BillingRecordResponse> history(@PathVariable String zoneId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return billingRecords.findByZone(zoneId, Limit.of(limit)).stream().map(BillingRecordResponse::from).toList();
    }

    @GetMapping("/zones/{zoneId}/transactions")
    public List<TransactionResponse> transactions(@PathVariable String zoneId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return transactions.findByZone(zoneId, Limit.of(limit)).stream().map(TransactionResponse::from).toList();
    }

    /**
     * Buys a new plant against the shared Grid wallet. The price is never taken from the request
     * -- it is computed here from {@link PlantPricing}, keyed on the same type and capacity
     * Producer will build, so the frontend's own estimate can never under- or over-charge it.
     * Unlike a billing charge, this is refused (402) rather than applied when the wallet can't
     * cover it; see {@link WalletSpendingService}.
     */
    @PostMapping("/plants/purchase")
    public PlantSpendResponse purchasePlant(@Valid @RequestBody PlantPurchaseRequest request) {
        double sold = unlocks.cumulativeKwhSold();
        if (!UnlockThresholds.isUnlocked(request.plantType(), sold)) {
            throw new PlantTypeLockedException(request.plantType(), UnlockThresholds.thresholdFor(request.plantType()), sold);
        }

        double cost = PlantPricing.cost(request.plantType(), request.capacityMw());
        double balance = spending.spend(GRID_WALLET_ID, GRID_WALLET_NAME, cost, TransactionType.PLANT_PURCHASE);
        return new PlantSpendResponse(GRID_WALLET_ID, GRID_WALLET_NAME, cost, balance, Instant.now());
    }

    /**
     * Grows an existing plant's capacity, charged only the difference between its old and new
     * price -- see {@link PlantUpgradeRequest}. A change that doesn't raise the price (a downgrade,
     * or no capacity change) costs nothing and is never sent to {@link WalletSpendingService}, so
     * it can't be refused for insufficient funds.
     */
    @PostMapping("/plants/upgrade")
    public PlantSpendResponse upgradePlant(@Valid @RequestBody PlantUpgradeRequest request) {
        double oldCost = PlantPricing.cost(request.plantType(), request.oldCapacityMw());
        double newCost = PlantPricing.cost(request.plantType(), request.newCapacityMw());
        double extraCost = Math.max(0.0, newCost - oldCost);

        double balance = extraCost > 0
                ? spending.spend(GRID_WALLET_ID, GRID_WALLET_NAME, extraCost, TransactionType.PLANT_UPGRADE)
                : wallets.findById(GRID_WALLET_ID).map(w -> w.getBalanceRupees()).orElse(startingBalance);

        return new PlantSpendResponse(GRID_WALLET_ID, GRID_WALLET_NAME, extraCost, balance, Instant.now());
    }

    /**
     * Decommissions a plant, crediting the shared Grid wallet a fraction of what it would cost to
     * buy that plant fresh today -- {@code billing.decommission-refund-ratio} of {@link
     * PlantPricing#cost}. Called before Producer's own {@code DELETE /api/plants/{id}}, the same
     * two-step client-side ordering already used in reverse for a purchase (pay Billing, then
     * create in Producer). A credit can never be refused, so unlike a purchase there is no 402
     * path here.
     */
    @PostMapping("/plants/decommission")
    public PlantSpendResponse decommissionPlant(@Valid @RequestBody PlantDecommissionRequest request) {
        double refund = decommissionRefundRatio * PlantPricing.cost(request.plantType(), request.capacityMw());
        double balance = spending.credit(GRID_WALLET_ID, GRID_WALLET_NAME, refund, TransactionType.PLANT_DECOMMISSION);
        return new PlantSpendResponse(GRID_WALLET_ID, GRID_WALLET_NAME, refund, balance, Instant.now());
    }

    /** Buys a new storage unit against the shared Grid wallet -- same shape as {@link
     *  #purchasePlant}, no unlock gate (storage isn't tech-tree gated). */
    @PostMapping("/storage/purchase")
    public PlantSpendResponse purchaseStorage(@Valid @RequestBody StoragePurchaseRequest request) {
        double cost = StoragePricing.cost(request.kind(), request.capacityKwh());
        double balance = spending.spend(GRID_WALLET_ID, GRID_WALLET_NAME, cost, TransactionType.STORAGE_PURCHASE);
        return new PlantSpendResponse(GRID_WALLET_ID, GRID_WALLET_NAME, cost, balance, Instant.now());
    }

    /** Which plant types are currently purchasable, and how far off the next one is -- see
     *  {@link UnlockService}. The frontend uses this to grey out a locked type in {@code
     *  AddPlantModal}, but {@link #purchasePlant} enforces the same rule server-side regardless. */
    @GetMapping("/unlocks")
    public UnlocksResponse unlocks() {
        double sold = unlocks.cumulativeKwhSold();
        List<PlantType> unlockedTypes = Arrays.stream(PlantType.values())
                .filter(type -> UnlockThresholds.isUnlocked(type, sold))
                .toList();
        UnlocksResponse.NextUnlock next = unlocks.nextLocked()
                .map(type -> new UnlocksResponse.NextUnlock(type, UnlockThresholds.thresholdFor(type) - sold))
                .orElse(null);

        return new UnlocksResponse(unlockedTypes, sold, next);
    }

    /** Revenue vs. spend, all time -- the frontend's live scoreboard panel reads this for its
     *  "cost efficiency" figure. No new persistence: both sides are summed fresh from {@link
     *  WalletTransactionRepository} on every request. */
    @GetMapping("/summary")
    public BillingSummaryResponse summary() {
        double revenue = transactions.sumByTypes(List.of(TransactionType.BILL_DEBIT));
        double spend = transactions.sumByTypes(List.of(
                TransactionType.PLANT_PURCHASE, TransactionType.PLANT_UPGRADE,
                TransactionType.PLANT_MAINTENANCE, TransactionType.STORAGE_PURCHASE));
        return new BillingSummaryResponse(revenue, spend);
    }
}
