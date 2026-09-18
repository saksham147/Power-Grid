package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import Billing.model.BillingDailyRollupRepository;
import Billing.model.BillingRecordRepository;

/** The running total sums raw and rolled-up kWh without double-counting, and the unlock/next-
 *  locked queries are consistent with {@link UnlockThresholds}. */
class UnlockServiceTests {

    @Test
    void cumulativeSoldIsRawPlusRolledUp() {
        var records = mock(BillingRecordRepository.class);
        var rollups = mock(BillingDailyRollupRepository.class);
        given(records.sumKwh()).willReturn(12_000.0);
        given(rollups.sumTotalKwh()).willReturn(38_000.0);

        var service = new UnlockService(records, rollups);

        assertThat(service.cumulativeKwhSold()).isEqualTo(50_000.0);
    }

    @Test
    void aTypeIsUnlockedOnceItsThresholdIsReached() {
        var records = mock(BillingRecordRepository.class);
        var rollups = mock(BillingDailyRollupRepository.class);
        given(records.sumKwh()).willReturn(50_000.0);
        given(rollups.sumTotalKwh()).willReturn(0.0);

        var service = new UnlockService(records, rollups);

        assertThat(service.isUnlocked(PlantType.THERMAL)).isTrue();
        assertThat(service.isUnlocked(PlantType.SOLAR)).isTrue();
        assertThat(service.isUnlocked(PlantType.WIND)).isFalse();
        assertThat(service.nextLocked()).contains(PlantType.WIND);
    }

    @Test
    void nothingLockedReportsEmpty() {
        var records = mock(BillingRecordRepository.class);
        var rollups = mock(BillingDailyRollupRepository.class);
        given(records.sumKwh()).willReturn(1_000_000.0);
        given(rollups.sumTotalKwh()).willReturn(0.0);

        var service = new UnlockService(records, rollups);

        assertThat(service.nextLocked()).isEmpty();
    }
}
