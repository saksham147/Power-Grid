package Billing.model;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import Billing.billing.BillingLedger;
import Billing.billing.BillingResult;

/**
 * The {@link BillingLedger} adapter: checks the idempotency guard, debits the wallet (creating it
 * with the starting balance on a zone's first-ever charge), and writes both the billing record and
 * the wallet transaction -- all as one transaction, so a crash midway cannot leave a debit with no
 * record of why or a record with no matching debit.
 */
@Component
public class JpaBillingLedger implements BillingLedger {

    private final WalletRepository wallets;
    private final BillingRecordRepository billingRecords;
    private final WalletTransactionRepository transactions;
    private final double startingBalance;

    public JpaBillingLedger(WalletRepository wallets, BillingRecordRepository billingRecords,
            WalletTransactionRepository transactions,
            @Value("${billing.starting-balance}") double startingBalance) {
        this.wallets = wallets;
        this.billingRecords = billingRecords;
        this.transactions = transactions;
        this.startingBalance = startingBalance;
    }

    @Override
    @Transactional
    public Optional<BillingResult> apply(String zoneId, String zoneName, long tick, double kwh, double overageKwh,
            double ratePerKwh, double costRupees, double overageCostRupees, Instant timestamp) {
        if (billingRecords.existsByZoneIdAndTickNumber(zoneId, tick)) {
            return Optional.empty();
        }

        Wallet wallet = wallets.findById(zoneId)
                .orElseGet(() -> wallets.save(new Wallet(zoneId, zoneName, startingBalance)));

        // Managed entity either way (freshly saved, or found): Hibernate dirty-checks and
        // flushes both mutations at commit, matching how PowerPlantController.setActive writes
        // back without a second save call.
        wallet.renameTo(zoneName);
        wallet.debit(costRupees);

        billingRecords.save(new BillingRecord(
                zoneId, zoneName, tick, kwh, overageKwh, ratePerKwh, costRupees, overageCostRupees, timestamp));
        transactions.save(new WalletTransaction(
                zoneId, TransactionType.BILL_DEBIT, costRupees, wallet.getBalanceRupees(), timestamp));

        return Optional.of(new BillingResult(zoneId, zoneName, tick, kwh, overageKwh, ratePerKwh, costRupees,
                overageCostRupees, wallet.getBalanceRupees(), timestamp));
    }
}
