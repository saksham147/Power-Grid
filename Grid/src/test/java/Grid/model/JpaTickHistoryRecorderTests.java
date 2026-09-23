package Grid.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The idempotency guard and the "never let a history write break the tick" contract -- see this
 * class's own doc comment for why the second one matters here specifically.
 */
class JpaTickHistoryRecorderTests {

    private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");

    private final TickRecordRepository repository = mock(TickRecordRepository.class);
    private final JpaTickHistoryRecorder recorder = new JpaTickHistoryRecorder(repository);

    @Test
    void recordsATickThatHasNotBeenSeenBefore() {
        given(repository.existsByTickNumber(42L)).willReturn(false);

        recorder.record(42L, "12:00", 1L, -0.1, 900.0, 1000.0, true, AT);

        var saved = ArgumentCaptor.forClass(TickRecord.class);
        verify(repository).save(saved.capture());
        TickRecord record = saved.getValue();
        assertThat(record.getTickNumber()).isEqualTo(42L);
        assertThat(record.getSimulatedTime()).isEqualTo("12:00");
        assertThat(record.getSimulatedDay()).isEqualTo(1L);
        assertThat(record.getFrequencyDeviation()).isEqualTo(-0.1);
        assertThat(record.getTotalSupplyKw()).isEqualTo(900.0);
        assertThat(record.getTotalDemandKw()).isEqualTo(1000.0);
        assertThat(record.isLoadExceeded()).isTrue();
        assertThat(record.getRecordedAt()).isEqualTo(AT);
    }

    @Test
    void aTickAlreadySeenIsNeverSavedAgain() {
        given(repository.existsByTickNumber(42L)).willReturn(true);

        recorder.record(42L, "12:00", 1L, -0.1, 900.0, 1000.0, true, AT);

        verify(repository, never()).save(any());
    }

    @Test
    void aFailingSaveIsSwallowedNotPropagated() {
        given(repository.existsByTickNumber(1L)).willReturn(false);
        given(repository.save(any())).willThrow(new RuntimeException("db is down"));

        assertThatCode(() -> recorder.record(1L, "00:00", 0L, 0.0, 0.0, 0.0, false, AT))
                .doesNotThrowAnyException();
    }
}
