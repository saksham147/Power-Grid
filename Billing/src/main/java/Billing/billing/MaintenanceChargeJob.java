package Billing.billing;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import Billing.api.WalletController;
import Billing.model.TransactionType;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * Charges the shared Grid wallet a recurring upkeep cost for every active plant, on top of
 * whatever the plant already cost to build. Mirrors {@code Billing.history.BillingRollupJob}'s
 * {@code @Scheduled} shape: an ISO-8601 interval string, an {@code enabled} guard, try/catch-swallow
 * so one bad run doesn't cancel the schedule, and a {@link TransactionTemplate} to run the charge
 * in a transaction (see {@link #runOnce()} for why an annotation would not).
 *
 * <p>
 * This is a mandatory charge, not a voluntary spend -- a plant can't be "not maintained" to avoid
 * the bill, the same reasoning {@code Billing.model.JpaBillingLedger} already applies to
 * consumption charges. It debits {@link Wallet} directly rather than going through {@code
 * WalletSpendingService#spend}, which would refuse the charge (402-style) if the Grid wallet
 * couldn't cover it -- a mandatory charge is never refused, it just pushes the balance further
 * negative, exactly like {@code JpaBillingLedger#apply}.
 */
@Component
public class MaintenanceChargeJob {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceChargeJob.class);

    private final PlantRosterCache roster;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final double startingBalance;

    public MaintenanceChargeJob(PlantRosterCache roster, WalletRepository wallets,
            WalletTransactionRepository transactions, TransactionTemplate transactionTemplate,
            @Value("${billing.maintenance.enabled:true}") boolean enabled,
            @Value("${billing.starting-balance}") double startingBalance) {
        this.roster = roster;
        this.wallets = wallets;
        this.transactions = transactions;
        this.transactionTemplate = transactionTemplate;
        this.enabled = enabled;
        this.startingBalance = startingBalance;
    }

    /**
     * Runs the charge inside a transaction it opens itself. A {@code @Transactional} annotation on
     * this method would do nothing: {@link #runScheduled} calls it on {@code this}, which never
     * passes through Spring's transaction proxy. That is exactly how this job once wrote its ledger
     * rows without ever changing the wallet balance -- the wallet was loaded outside any
     * transaction, debited in memory, and the change was never flushed. {@code
     * Billing.history.BillingRollupJob} uses a {@link TransactionTemplate} for the same reason.
     */
    double runOnce() {
        Double charged = transactionTemplate.execute(status -> chargeUpkeep());
        return charged == null ? 0.0 : charged;
    }

    private double chargeUpkeep() {
        double totalCost = roster.activePlants().stream()
                .mapToDouble(p -> MaintenancePricing.costFor(p.type(), p.capacityMw()))
                .sum();

        if (totalCost <= 0) {
            return 0.0;
        }

        Wallet wallet = wallets.findByIdForUpdate(WalletController.GRID_WALLET_ID)
                .orElseGet(() -> wallets.save(
                        new Wallet(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, startingBalance)));

        wallet.debit(totalCost);
        transactions.save(new WalletTransaction(WalletController.GRID_WALLET_ID, TransactionType.PLANT_MAINTENANCE,
                totalCost, wallet.getBalanceRupees(), Instant.now()));

        return totalCost;
    }

    /** No {@code initialDelay}: matches {@code BillingRollupJob}'s "don't wait out a full interval
     *  before the first charge" reasoning. */
    @Scheduled(fixedDelayString = "${billing.maintenance.interval:PT10M}")
    void runScheduled() {
        if (!enabled) {
            return;
        }
        try {
            double charged = runOnce();
            if (charged > 0) {
                log.info("Charged {} in plant maintenance to the Grid wallet", charged);
            }
        } catch (Exception e) {
            // Swallowed for the same reason every other @Scheduled method in this project does
            // this: an escaping exception cancels all future runs on this scheduler.
            log.error("Maintenance charge run failed; will retry next interval", e);
        }
    }
}
