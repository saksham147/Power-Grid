package Billing.billing;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Billing.model.TransactionType;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * Voluntary spending from a zone's wallet -- a plant purchase today, a future grid-creation cost
 * potentially later. Deliberately separate from {@link BillingCycleService}/{@link BillingLedger}:
 * a billing charge always applies, because a zone cannot refuse to have consumed power it already
 * consumed; a spend can be refused, because nothing forces a zone to buy a plant it can't afford.
 * Collapsing the two would mean either letting consumption charges bounce (wrong) or letting
 * purchases push a wallet into debt (also wrong).
 */
@Service
public class WalletSpendingService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final double startingBalance;

    public WalletSpendingService(WalletRepository wallets, WalletTransactionRepository transactions,
            @Value("${billing.starting-balance}") double startingBalance) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.startingBalance = startingBalance;
    }

    /** @see #spend(String, String, double, TransactionType) -- defaults to a plant purchase. */
    @Transactional
    public double spend(String zoneId, String zoneName, double amountRupees) {
        return spend(zoneId, zoneName, amountRupees, TransactionType.PLANT_PURCHASE);
    }

    /**
     * @return the balance after the spend
     * @throws InsufficientFundsException if the wallet cannot cover {@code amountRupees}
     */
    @Transactional
    public double spend(String zoneId, String zoneName, double amountRupees, TransactionType type) {
        Wallet wallet = wallets.findById(zoneId)
                .orElseGet(() -> wallets.save(new Wallet(zoneId, zoneName, startingBalance)));

        if (wallet.getBalanceRupees() < amountRupees) {
            throw new InsufficientFundsException(zoneId, wallet.getBalanceRupees(), amountRupees);
        }

        wallet.renameTo(zoneName);
        wallet.debit(amountRupees);

        transactions.save(new WalletTransaction(
                zoneId, type, amountRupees, wallet.getBalanceRupees(), Instant.now()));

        return wallet.getBalanceRupees();
    }

    /**
     * The symmetric counterpart to {@link #spend} -- a credit can never fail, so unlike a spend
     * there is no {@link InsufficientFundsException} path here.
     *
     * @return the balance after the credit
     */
    @Transactional
    public double credit(String zoneId, String zoneName, double amountRupees, TransactionType type) {
        Wallet wallet = wallets.findById(zoneId)
                .orElseGet(() -> wallets.save(new Wallet(zoneId, zoneName, startingBalance)));

        wallet.renameTo(zoneName);
        wallet.credit(amountRupees);

        transactions.save(new WalletTransaction(
                zoneId, type, amountRupees, wallet.getBalanceRupees(), Instant.now()));

        return wallet.getBalanceRupees();
    }
}
