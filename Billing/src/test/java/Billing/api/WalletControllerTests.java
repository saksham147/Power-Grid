package Billing.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Billing.billing.InsufficientFundsException;
import Billing.billing.UnlockService;
import Billing.billing.WalletSpendingService;
import Billing.model.BillingRecord;
import Billing.model.BillingRecordRepository;
import Billing.model.TransactionType;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * Contract of the read-only billing endpoints: a slice with the repositories mocked, so this runs
 * with no Kafka and no Postgres.
 */
@WebMvcTest(WalletController.class)
class WalletControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletRepository wallets;

    @MockitoBean
    private BillingRecordRepository billingRecords;

    @MockitoBean
    private WalletTransactionRepository transactions;

    @MockitoBean
    private WalletSpendingService spending;

    @MockitoBean
    private UnlockService unlocks;

    @Test
    void listsEveryWallet() throws Exception {
        given(wallets.findAll()).willReturn(List.of(new Wallet("Z-N", "North", 9200.0)));

        mockMvc.perform(get("/api/billing/wallets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneId").value("Z-N"))
                .andExpect(jsonPath("$[0].zoneName").value("North"))
                .andExpect(jsonPath("$[0].balanceRupees").value(9200.0));
    }

    @Test
    void returnsOneZonesWallet() throws Exception {
        given(wallets.findById("Z-N")).willReturn(java.util.Optional.of(new Wallet("Z-N", "North", 9200.0)));

        mockMvc.perform(get("/api/billing/zones/Z-N/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceRupees").value(9200.0));
    }

    @Test
    void aZoneNeverBilledReturns404() throws Exception {
        given(wallets.findById("Z-GHOST")).willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/billing/zones/Z-GHOST/wallet"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsBillingHistoryNewestFirst() throws Exception {
        given(billingRecords.findByZone(org.mockito.ArgumentMatchers.eq("Z-N"), org.mockito.ArgumentMatchers.any(Limit.class)))
                .willReturn(List.of(new BillingRecord("Z-N", "North", 5, 10.0, 0.0, 8.0, 80.0, 0.0, Instant.now())));

        mockMvc.perform(get("/api/billing/zones/Z-N/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tickNumber").value(5))
                .andExpect(jsonPath("$[0].kwh").value(10.0))
                .andExpect(jsonPath("$[0].costRupees").value(80.0));
    }

    @Test
    void returnsTransactionHistory() throws Exception {
        given(transactions.findByZone(org.mockito.ArgumentMatchers.eq("Z-N"), org.mockito.ArgumentMatchers.any(Limit.class)))
                .willReturn(List.of(new WalletTransaction("Z-N", TransactionType.BILL_DEBIT, 80.0, 9200.0, Instant.now())));

        mockMvc.perform(get("/api/billing/zones/Z-N/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BILL_DEBIT"))
                .andExpect(jsonPath("$[0].amountRupees").value(80.0))
                .andExpect(jsonPath("$[0].balanceAfterRupees").value(9200.0));
    }

    @Test
    void purchasingWithinBalanceChargesThePricingFormulaNotTheRequest() throws Exception {
        // 900 MW thermal (a realistic plant size in this simulation): 2000 + 300*8 + 300*10 +
        // 300*14 = 11600 (see PlantPricingTests for the banded breakdown). No zone is named -- a
        // plant belongs to no zone, so this always charges the shared Grid wallet.
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 11600.0,
                TransactionType.PLANT_PURCHASE)).willReturn(1200.0);

        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"capacityMw\":900.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value("GRID"))
                .andExpect(jsonPath("$.amountRupees").value(11600.0))
                .andExpect(jsonPath("$.balanceRupees").value(1200.0));
    }

    @Test
    void aTinyPlantIsChargedTheFloorNotAnAlmostFreePrice() throws Exception {
        // 0.5 MW solar: max(2000, 500 + 8*0.5) = 2000, the minimum. Sold figure set past SOLAR's
        // unlock threshold so this test exercises pricing, not the unlock gate.
        given(unlocks.cumulativeKwhSold()).willReturn(100_000.0);
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 2000.0,
                TransactionType.PLANT_PURCHASE)).willReturn(7200.0);

        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"SOLAR\",\"capacityMw\":0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountRupees").value(2000.0));
    }

    @Test
    void purchasingBeyondBalanceIsRejectedWith402() throws Exception {
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 11600.0,
                TransactionType.PLANT_PURCHASE))
                .willThrow(new InsufficientFundsException(WalletController.GRID_WALLET_ID, 500.0, 11600.0));

        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"capacityMw\":900.0}"))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aNonPositiveCapacityIsRejected() throws Exception {
        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"capacityMw\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void upgradingChargesOnlyTheDifferenceBetweenOldAndNewPrice() throws Exception {
        // Thermal 300 -> 900 MW: new 11600 (banded) - old max(5000, 2000+300*8)=5000 = 6600.
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 6600.0,
                TransactionType.PLANT_UPGRADE)).willReturn(4000.0);

        mockMvc.perform(post("/api/billing/plants/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"oldCapacityMw\":300.0,\"newCapacityMw\":900.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountRupees").value(6600.0))
                .andExpect(jsonPath("$.balanceRupees").value(4000.0));
    }

    @Test
    void shrinkingAPlantIsFreeAndNeverChargesTheWallet() throws Exception {
        given(wallets.findById(WalletController.GRID_WALLET_ID))
                .willReturn(java.util.Optional.of(new Wallet(WalletController.GRID_WALLET_ID,
                        WalletController.GRID_WALLET_NAME, 4000.0)));

        mockMvc.perform(post("/api/billing/plants/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"oldCapacityMw\":900.0,\"newCapacityMw\":300.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountRupees").value(0.0))
                .andExpect(jsonPath("$.balanceRupees").value(4000.0));

        org.mockito.Mockito.verifyNoInteractions(spending);
    }

    @Test
    void upgradingBeyondBalanceIsRejectedWith402() throws Exception {
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 6600.0,
                TransactionType.PLANT_UPGRADE))
                .willThrow(new InsufficientFundsException(WalletController.GRID_WALLET_ID, 500.0, 6600.0));

        mockMvc.perform(post("/api/billing/plants/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"oldCapacityMw\":300.0,\"newCapacityMw\":900.0}"))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void purchasingALockedTypeIsRejectedWith403() throws Exception {
        given(unlocks.cumulativeKwhSold()).willReturn(1000.0);

        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"WIND\",\"capacityMw\":150.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());

        org.mockito.Mockito.verifyNoInteractions(spending);
    }

    @Test
    void anUnlockedTypeIsStillCheckedButAllowedThrough() throws Exception {
        given(unlocks.cumulativeKwhSold()).willReturn(0.0);
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 11600.0,
                TransactionType.PLANT_PURCHASE)).willReturn(1200.0);

        mockMvc.perform(post("/api/billing/plants/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"capacityMw\":900.0}"))
                .andExpect(status().isOk());
    }

    @Test
    void unlocksReportsProgressTowardTheNextLockedType() throws Exception {
        given(unlocks.cumulativeKwhSold()).willReturn(30_000.0);
        given(unlocks.nextLocked()).willReturn(java.util.Optional.of(Billing.billing.PlantType.SOLAR));

        mockMvc.perform(get("/api/billing/unlocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlockedTypes").isArray())
                .andExpect(jsonPath("$.unlockedTypes[0]").value("THERMAL"))
                .andExpect(jsonPath("$.cumulativeKwhSold").value(30000.0))
                .andExpect(jsonPath("$.nextUnlock.type").value("SOLAR"))
                .andExpect(jsonPath("$.nextUnlock.kwhRemaining").value(20000.0));
    }

    @Test
    void purchasingStorageChargesTheStoragePricingFormula() throws Exception {
        // 1000 kWh battery: 1500 + 5*1000 = 6500.
        given(spending.spend(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 6500.0,
                TransactionType.STORAGE_PURCHASE)).willReturn(3500.0);

        mockMvc.perform(post("/api/billing/storage/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"BATTERY\",\"capacityKwh\":1000.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value("GRID"))
                .andExpect(jsonPath("$.amountRupees").value(6500.0))
                .andExpect(jsonPath("$.balanceRupees").value(3500.0));
    }

    @Test
    void summaryReportsRevenueAndSpendSeparately() throws Exception {
        given(transactions.sumByTypes(java.util.List.of(TransactionType.BILL_DEBIT))).willReturn(80_000.0);
        given(transactions.sumByTypes(java.util.List.of(TransactionType.PLANT_PURCHASE, TransactionType.PLANT_UPGRADE,
                TransactionType.PLANT_MAINTENANCE, TransactionType.STORAGE_PURCHASE))).willReturn(35_000.0);

        mockMvc.perform(get("/api/billing/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueRupees").value(80000.0))
                .andExpect(jsonPath("$.spendRupees").value(35000.0));
    }

    @Test
    void decommissioningCreditsHalfTheFreshBuildPrice() throws Exception {
        // 900 MW thermal: 11600 (banded, see PlantPricingTests); refund ratio 0.5 -> 5800.
        given(spending.credit(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 5800.0,
                TransactionType.PLANT_DECOMMISSION)).willReturn(16500.0);

        mockMvc.perform(post("/api/billing/plants/decommission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plantType\":\"THERMAL\",\"capacityMw\":900.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value("GRID"))
                .andExpect(jsonPath("$.amountRupees").value(5800.0))
                .andExpect(jsonPath("$.balanceRupees").value(16500.0));
    }
}
