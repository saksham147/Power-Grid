package Billing.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import Billing.api.WalletController;

/**
 * Every customer bill is two movements of the same amount: the zone wallet goes down and the Grid
 * Treasury goes up. Without the second, revenue would leave the customer and arrive nowhere.
 */
class JpaBillingLedgerTests {

    private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");

    private final WalletRepository wallets = mock(WalletRepository.class);
    private final BillingRecordRepository billingRecords = mock(BillingRecordRepository.class);
    private final WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
    private final JpaBillingLedger ledger = new JpaBillingLedger(wallets, billingRecords, transactions, 10000.0);

    private Wallet zone = new Wallet("Z-E", "East", 10000.0);
    private Wallet treasury = new Wallet(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 50000.0);

    private void givenBothWalletsExist() {
        given(wallets.findById("Z-E")).willReturn(Optional.of(zone));
        given(wallets.findByIdForUpdate(WalletController.GRID_WALLET_ID)).willReturn(Optional.of(treasury));
    }

    @Test
    void aBillDebitsTheZoneAndCreditsTheTreasuryByTheSameAmount() {
        givenBothWalletsExist();

        var result = ledger.apply("Z-E", "East", 42L, 100.0, 0.0, 8.0, 800.0, 0.0, AT);

        assertThat(result).isPresent();
        assertThat(zone.getBalanceRupees()).isEqualTo(9200.0);
        assertThat(treasury.getBalanceRupees()).isEqualTo(50800.0);

        var saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).satisfiesExactly(
                debit -> {
                    assertThat(debit.getZoneId()).isEqualTo("Z-E");
                    assertThat(debit.getType()).isEqualTo(TransactionType.BILL_DEBIT);
                    assertThat(debit.getAmountRupees()).isEqualTo(800.0);
                    assertThat(debit.getBalanceAfterRupees()).isEqualTo(9200.0);
                },
                credit -> {
                    assertThat(credit.getZoneId()).isEqualTo(WalletController.GRID_WALLET_ID);
                    assertThat(credit.getType()).isEqualTo(TransactionType.BILL_REVENUE);
                    assertThat(credit.getAmountRupees()).isEqualTo(800.0);
                    assertThat(credit.getBalanceAfterRupees()).isEqualTo(50800.0);
                });
    }

    @Test
    void theSurchargeIsPartOfWhatTheTreasuryEarns() {
        givenBothWalletsExist();

        // 900 total, of which 300 is over-capacity surcharge -- the customer pays all 900.
        ledger.apply("Z-E", "East", 43L, 100.0, 20.0, 8.0, 900.0, 300.0, AT);

        assertThat(zone.getBalanceRupees()).isEqualTo(9100.0);
        assertThat(treasury.getBalanceRupees()).isEqualTo(50900.0);
    }

    @Test
    void aTreasuryThatDoesNotExistYetIsCreatedAndCredited() {
        given(wallets.findById("Z-E")).willReturn(Optional.of(zone));
        given(wallets.findByIdForUpdate(WalletController.GRID_WALLET_ID)).willReturn(Optional.empty());
        given(wallets.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));

        ledger.apply("Z-E", "East", 44L, 100.0, 0.0, 8.0, 800.0, 0.0, AT);

        var created = ArgumentCaptor.forClass(Wallet.class);
        verify(wallets).save(created.capture());
        assertThat(created.getValue().getZoneId()).isEqualTo(WalletController.GRID_WALLET_ID);
        // The configured starting balance (10000), then the bill on top of it.
        assertThat(created.getValue().getBalanceRupees()).isEqualTo(10800.0);
    }

    @Test
    void aTickThatWasAlreadyBilledChangesNothingAtAll() {
        given(billingRecords.existsByZoneIdAndTickNumber("Z-E", 42L)).willReturn(true);
        givenBothWalletsExist();

        var result = ledger.apply("Z-E", "East", 42L, 100.0, 0.0, 8.0, 800.0, 0.0, AT);

        assertThat(result).isEmpty();
        assertThat(zone.getBalanceRupees()).isEqualTo(10000.0);
        assertThat(treasury.getBalanceRupees()).isEqualTo(50000.0);
        verify(transactions, never()).save(any());
        verify(wallets, never()).findByIdForUpdate(any());
    }

    @Test
    void moneyIsConservedAcrossABillingRun() {
        givenBothWalletsExist();
        double before = zone.getBalanceRupees() + treasury.getBalanceRupees();

        for (long tick = 1; tick <= 20; tick++) {
            ledger.apply("Z-E", "East", tick, 50.0, 0.0, 8.0, 400.0 + tick, 0.0, AT);
        }

        // Customers paid exactly what the grid earned: the two wallets together are unchanged.
        assertThat(zone.getBalanceRupees() + treasury.getBalanceRupees()).isEqualTo(before);
        assertThat(List.of(zone.getBalanceRupees(), treasury.getBalanceRupees())).allSatisfy(b -> assertThat(b).isNotNaN());
    }
}
