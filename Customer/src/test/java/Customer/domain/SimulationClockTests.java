package Customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** The tick-to-time contract everything else depends on. */
class SimulationClockTests {

    private final SimulationClock clock = new SimulationClock(5, 1);

    @Test
    void derivesTickPaceFromTheSimulationSpeed() {
        assertThat(clock.ticksPerDay()).isEqualTo(288);
        // 5 simulated minutes at 1 real second each.
        assertThat(clock.realSecondsPerTick()).isEqualTo(5);

        // Double speed halves the wall-clock gap without changing simulated time.
        var faster = new SimulationClock(5, 2);
        assertThat(faster.ticksPerDay()).isEqualTo(288);
        assertThat(faster.realSecondsPerTick()).isEqualTo(10);
    }

    @Test
    void mapsTicksOntoTheDay() {
        assertThat(clock.timeOfDay(0)).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(clock.timeOfDay(72)).isEqualTo(LocalTime.of(6, 0));
        assertThat(clock.timeOfDay(144)).isEqualTo(LocalTime.of(12, 0));
        assertThat(clock.timeOfDay(216)).isEqualTo(LocalTime.of(18, 0));
        assertThat(clock.timeOfDay(288)).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void formatsWithoutSeconds() {
        assertThat(clock.formatTimeOfDay(0)).isEqualTo("00:00");
        assertThat(clock.formatTimeOfDay(1)).isEqualTo("00:05");
        assertThat(clock.formatTimeOfDay(287)).isEqualTo("23:55");
    }

    /** Day 0 is a Monday, so a weekend is reachable without waiting on a real calendar. */
    @Test
    void countsDaysAndReachesTheWeekend() {
        assertThat(clock.dayNumber(287)).isZero();
        assertThat(clock.dayNumber(288)).isEqualTo(1);

        assertThat(clock.dayOfWeek(0)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(clock.dayOfWeek(5 * 288)).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(clock.dayOfWeek(6 * 288)).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(clock.dayOfWeek(7 * 288)).isEqualTo(DayOfWeek.MONDAY);
    }

    /**
     * A tick size that does not divide the day would put the daily profile a little
     * further out of
     * phase every simulated day, which is the kind of fault that only shows up a
     * week in.
     */
    @Test
    void rejectsATickSizeThatDoesNotDivideTheDay() {
        assertThatThrownBy(() -> new SimulationClock(7, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1440");

        assertThatThrownBy(() -> new SimulationClock(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimulationClock(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
