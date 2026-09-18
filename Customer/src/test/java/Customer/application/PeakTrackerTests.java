package Customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** The day/night split and the running-maximum, reset-once-per-simulated-day behaviour. */
class PeakTrackerTests {

    @Test
    void tracksTheHighestReadingSeenSoFarInEachWindow() {
        var tracker = new PeakTracker();

        tracker.record("Z-N", 100.0, LocalTime.of(9, 0), 0);
        tracker.record("Z-N", 150.0, LocalTime.of(14, 0), 0);
        tracker.record("Z-N", 80.0, LocalTime.of(11, 0), 0);
        tracker.record("Z-N", 60.0, LocalTime.of(22, 0), 0);
        tracker.record("Z-N", 90.0, LocalTime.of(2, 0), 0);

        assertThat(tracker.dayPeakKw("Z-N")).isEqualTo(150.0);
        assertThat(tracker.nightPeakKw("Z-N")).isEqualTo(90.0);
    }

    @Test
    void aNewSimulatedDayResetsEveryZonesPeaks() {
        var tracker = new PeakTracker();

        tracker.record("Z-N", 500.0, LocalTime.of(12, 0), 0);
        assertThat(tracker.dayPeakKw("Z-N")).isEqualTo(500.0);

        tracker.record("Z-N", 50.0, LocalTime.of(12, 0), 1);

        assertThat(tracker.dayPeakKw("Z-N")).isEqualTo(50.0);
    }

    @Test
    void anUnknownZoneReportsZeroRatherThanAnError() {
        var tracker = new PeakTracker();

        assertThat(tracker.dayPeakKw("Z-GHOST")).isZero();
        assertThat(tracker.nightPeakKw("Z-GHOST")).isZero();
    }
}
