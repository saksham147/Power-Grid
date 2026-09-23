package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import Billing.api.WalletController;
import Billing.model.TransactionType;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * The upkeep math and the mandatory-charge shape: it debits the Grid wallet even when nothing
 * refuses the charge, it does nothing at all when there is nothing active to charge for, and the
 * whole charge runs inside a transaction -- the property whose absence once let it write ledger
 * rows without ever changing the balance.
 */
class MaintenanceChargeJobTests {

    /** A template that simply runs the callback, so the job's own logic executes for real. */
    private static TransactionTemplate passThrough() {
        var template = mock(TransactionTemplate.class);
        given(template.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        return template;
    }

    @Test
    void chargesTheSumOfEveryActivePlantsUpkeepToTheGridWallet() {
        var roster = mock(PlantRosterCache.class);
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        // Thermal 900MW @ 30/MW = 27000; Wind 150MW @ 15/MW = 2250. Total 29250.
        given(roster.activePlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 900.0, true),
                new PlantRosterCache.PlantSnapshot(2L, PlantType.WIND, 150.0, true)));
        var treasury = new Wallet(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 40000.0);
        given(wallets.findByIdForUpdate(WalletController.GRID_WALLET_ID)).willReturn(Optional.of(treasury));

        var job = new MaintenanceChargeJob(roster, wallets, transactions, passThrough(), true, 10000.0);

        double charged = job.runOnce();

        assertThat(charged).isEqualTo(29250.0);
        // The balance itself moves -- not just a ledger row saying it did.
        assertThat(treasury.getBalanceRupees()).isEqualTo(40000.0 - 29250.0);
        var saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TransactionType.PLANT_MAINTENANCE);
        assertThat(saved.getValue().getAmountRupees()).isEqualTo(29250.0);
        assertThat(saved.getValue().getBalanceAfterRupees()).isEqualTo(treasury.getBalanceRupees());
    }

    @Test
    void theChargeRunsInsideATransaction() {
        var roster = mock(PlantRosterCache.class);
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        var template = passThrough();
        given(roster.activePlants()).willReturn(List.of());

        new MaintenanceChargeJob(roster, wallets, transactions, template, true, 10000.0).runOnce();

        verify(template).execute(any());
    }

    @Test
    void noActivePlantsChargesNothingAndNeverTouchesTheWallet() {
        var roster = mock(PlantRosterCache.class);
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);
        given(roster.activePlants()).willReturn(List.of());

        var job = new MaintenanceChargeJob(roster, wallets, transactions, passThrough(), true, 10000.0);

        double charged = job.runOnce();

        assertThat(charged).isEqualTo(0.0);
        verify(wallets, never()).findByIdForUpdate(any());
        verify(transactions, never()).save(any());
    }
}
