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

import Billing.api.WalletController;
import Billing.model.Wallet;
import Billing.model.WalletRepository;
import Billing.model.WalletTransaction;
import Billing.model.WalletTransactionRepository;

/**
 * The upkeep math and the mandatory-charge shape: it debits the Grid wallet even when nothing
 * refuses the charge, and it does nothing at all when there is nothing active to charge for.
 */
class MaintenanceChargeJobTests {

    @Test
    void chargesTheSumOfEveryActivePlantsUpkeepToTheGridWallet() {
        var roster = mock(PlantRosterCache.class);
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);

        // Thermal 900MW @ 6/MW = 5400; Wind 150MW @ 3/MW = 450. Total 5850.
        given(roster.activePlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 900.0, true),
                new PlantRosterCache.PlantSnapshot(2L, PlantType.WIND, 150.0, true)));
        given(wallets.findById(WalletController.GRID_WALLET_ID))
                .willReturn(Optional.of(new Wallet(WalletController.GRID_WALLET_ID, WalletController.GRID_WALLET_NAME, 20000.0)));

        var job = new MaintenanceChargeJob(roster, wallets, transactions, true, 10000.0);

        double charged = job.runOnce();

        assertThat(charged).isEqualTo(5850.0);
        verify(transactions).save(any(WalletTransaction.class));
    }

    @Test
    void noActivePlantsChargesNothingAndNeverTouchesTheWallet() {
        var roster = mock(PlantRosterCache.class);
        var wallets = mock(WalletRepository.class);
        var transactions = mock(WalletTransactionRepository.class);

        given(roster.activePlants()).willReturn(List.of());

        var job = new MaintenanceChargeJob(roster, wallets, transactions, true, 10000.0);

        double charged = job.runOnce();

        assertThat(charged).isEqualTo(0.0);
        verify(wallets, never()).findById(any());
        verify(transactions, never()).save(any());
    }
}
