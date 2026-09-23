package Grid.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import Grid.model.TickRecordRepository;

/** The retention cutoff arithmetic, and the same enabled-guard/swallow-on-failure shape every
 *  other scheduled job in this project uses. */
class TickHistoryPruneJobTests {

    private static final Instant NOW = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void deletesEverythingOlderThanTheRetentionWindow() {
        var repository = mock(TickRecordRepository.class);
        given(repository.deleteRecordedBefore(any())).willReturn(7);
        var job = new TickHistoryPruneJob(repository, Duration.ofHours(24), true);

        int deleted = job.pruneOlderThan(NOW);

        assertThat(deleted).isEqualTo(7);
        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteRecordedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void aDisabledJobNeverRunsOnSchedule() {
        var repository = mock(TickRecordRepository.class);
        var job = new TickHistoryPruneJob(repository, Duration.ofHours(24), false);

        job.runScheduled();

        verify(repository, never()).deleteRecordedBefore(any());
    }

    @Test
    void aFailingPruneIsSwallowedNotPropagated() {
        var repository = mock(TickRecordRepository.class);
        given(repository.deleteRecordedBefore(any())).willThrow(new RuntimeException("db is down"));
        var job = new TickHistoryPruneJob(repository, Duration.ofHours(24), true);

        assertThatCode(job::runScheduled).doesNotThrowAnyException();
    }
}
