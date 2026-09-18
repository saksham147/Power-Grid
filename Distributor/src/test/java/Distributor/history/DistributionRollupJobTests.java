package Distributor.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import Distributor.model.DistributionDailyRollupRepository;
import Distributor.model.DistributionRecordRepository;

/**
 * The cutoff-tick math and the empty/nothing-to-roll-yet guard -- the one part of this job that is
 * plain Java, not a native query only Postgres can actually run (see {@code
 * DistributionDailyRollupRepository#rollUpBefore}, which this project has no automated coverage
 * for either, matching {@code Producer.history.GenerationRollupJob}'s own precedent). {@link
 * TransactionTemplate#execute} is mocked to just invoke the callback, so no real transaction
 * manager is needed.
 */
class DistributionRollupJobTests {

    private static final long TICKS_PER_DAY = DistributionRollupJob.TICKS_PER_DAY;

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
        var records = mock(DistributionRecordRepository.class);
        var rollups = mock(DistributionDailyRollupRepository.class);
        var properties = new DistributionHistoryProperties(15, java.time.Duration.ofMinutes(10), true);
        var job = new DistributionRollupJob(records, rollups, fakeTransactions(), properties);

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
        var records = mock(DistributionRecordRepository.class);
        var rollups = mock(DistributionDailyRollupRepository.class);
        var properties = new DistributionHistoryProperties(15, java.time.Duration.ofMinutes(10), true);
        var job = new DistributionRollupJob(records, rollups, fakeTransactions(), properties);

        // 15 days = 4320 ticks; latest tick 4000 is not old enough for anything to have aged out.
        var result = job.rollUp(4000);

        assertThat(result).isEmpty();
        verify(rollups, never()).rollUpBefore(anyLong(), anyLong());
        verify(records, never()).deleteBefore(anyLong());
    }
}
