package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import Billing.model.TransactionType;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * Mockito over the two repositories rather than a fake port: {@link WalletSpendingService} is the
 * JPA adapter itself here (find-or-create, check, debit, record), not a use-case behind a port the
 * way {@link BillingCycleService} is -- so there is no smaller seam to fake against.
 */
class WalletSpendingServiceTests {

    private static final double STARTING_BALANCE = 10_000.0;

    @Test
    void spendingWithinBalanceDebitsAndRecordsATransaction() {
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        var service = new WalletSpendingService(wallets, transactions, STARTING_BALANCE);

        Wallet wallet = new Wallet("Z-N", "North", 5000.0);
        given(wallets.findByIdForUpdate("Z-N")).willReturn(Optional.of(wallet));

        double balanceAfter = service.spend("Z-N", "North", 2000.0);

        assertThat(balanceAfter).isEqualTo(3000.0);
        assertThat(wallet.getBalanceRupees()).isEqualTo(3000.0);
        verify(transactions).save(any(WalletTransaction.class));
    }

    @Test
    void spendingBeyondBalanceThrowsAndRecordsNothing() {
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        var service = new WalletSpendingService(wallets, transactions, STARTING_BALANCE);

        Wallet wallet = new Wallet("Z-N", "North", 500.0);
        given(wallets.findByIdForUpdate("Z-N")).willReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.spend("Z-N", "North", 2000.0))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalanceRupees()).isEqualTo(500.0);
        verify(transactions, never()).save(any());
    }

    @Test
    void firstSpendForAZoneLazilyCreatesItsWalletAtTheStartingBalance() {
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        var service = new WalletSpendingService(wallets, transactions, STARTING_BALANCE);

        given(wallets.findByIdForUpdate("Z-NEW")).willReturn(Optional.empty());
        given(wallets.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));

        double balanceAfter = service.spend("Z-NEW", "New Zone", 3000.0);

        assertThat(balanceAfter).isEqualTo(STARTING_BALANCE - 3000.0);
    }

    @Test
    void aTransactionTypeSpendRecordsAsPlantPurchase() {
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        var service = new WalletSpendingService(wallets, transactions, STARTING_BALANCE);

        given(wallets.findByIdForUpdate("Z-N")).willReturn(Optional.of(new Wallet("Z-N", "North", 5000.0)));

        service.spend("Z-N", "North", 100.0);

        var captor = org.mockito.ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.PLANT_PURCHASE);
    }
}
