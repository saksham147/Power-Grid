package Billing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import Billing.model.BillingDailyRollupRepository;
import Billing.model.BillingRecordRepository;

/**
 * The cutoff-tick math and the empty/nothing-to-roll-yet guard -- see {@code
 * Distributor.history.DistributionRollupJobTests} for why the native rollup query itself has no
 * automated coverage here (matching Producer's own precedent).
 */
class BillingRollupJobTests {

    private static final long TICKS_PER_DAY = BillingRollupJob.TICKS_PER_DAY;

    private static TransactionTemplate fakeTransactions() {
        var transactions = mock(TransactionTemplate.class);
        given(transactions.execute(org.mockito.ArgumentMatchers.<TransactionCallback<RollupResult>>any()))
                .willAnswer(invocation -> {
                    TransactionCallback<RollupResult> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
        return transactions;
    }

    @Test
    void rollsUpEverythingOlderThanRetentionDaysConvertedToTicks() {
        var records = mock(BillingRecordRepository.class);
        var rollups = mock(BillingDailyRollupRepository.class);
        var properties = new BillingHistoryProperties(15, Duration.ofMinutes(10), true);
        var job = new BillingRollupJob(records, rollups, fakeTransactions(), properties);

        // 15 simulated days = 15 * 288 = 4320 ticks; latest tick 5000 -> cutoff 680.
        given(rollups.rollUpBefore(680L, TICKS_PER_DAY)).willReturn(8);
        given(records.deleteBefore(680L)).willReturn(96);

        RollupResult result = job.rollUp(5000).orElseThrow();

        assertThat(result.cutoffTick()).isEqualTo(680L);
        assertThat(result.bucketsWritten()).isEqualTo(8);
        assertThat(result.rawDeleted()).isEqualTo(96);
    }

    @Test
    void nothingHasAgedOutYetSkipsTheTransactionEntirely() {
        var records = mock(BillingRecordRepository.class);
        var rollups = mock(BillingDailyRollupRepository.class);
        var properties = new BillingHistoryProperties(15, Duration.ofMinutes(10), true);
        var job = new BillingRollupJob(records, rollups, fakeTransactions(), properties);

        // 15 days = 4320 ticks; latest tick 4000 is not old enough for anything to have aged out.
        var result = job.rollUp(4000);

        assertThat(result).isEmpty();
        verify(rollups, never()).rollUpBefore(anyLong(), anyLong());
        verify(records, never()).deleteBefore(anyLong());
    }
}
