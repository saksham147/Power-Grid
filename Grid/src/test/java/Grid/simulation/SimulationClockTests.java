package Grid.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * The tick/time contract this whole system now shares. Every other service's own copy of this
 * logic (Producer's, Customer's) must agree with these numbers, since there is no shared library
 * module to enforce it for them.
 */
class SimulationClockTests {

    @Test
    void oneRealSecondIsOneSimulatedMinute() {
        assertThat(SimulationClock.SIMULATED_MINUTES_PER_TICK).isEqualTo(5);
        assertThat(SimulationClock.REAL_TIME_PER_TICK.toSeconds()).isEqualTo(5);
    }

    @Test
    void tickMapsOntoTheSimulatedDay() {
        assertThat(SimulationClock.timeOfDay(0)).isEqualTo(LocalTime.of(0, 0));
        assertThat(SimulationClock.timeOfDay(72)).isEqualTo(LocalTime.of(6, 0));
        assertThat(SimulationClock.timeOfDay(144)).isEqualTo(LocalTime.of(12, 0));
        assertThat(SimulationClock.timeOfDay(216)).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void theClockWrapsAtMidnightAndCountsDays() {
        assertThat(SimulationClock.timeOfDay(288)).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(SimulationClock.timeOfDay(288 + 72)).isEqualTo(LocalTime.of(6, 0));

        assertThat(SimulationClock.dayNumber(0)).isZero();
        assertThat(SimulationClock.dayNumber(287)).isZero();
        assertThat(SimulationClock.dayNumber(288)).isEqualTo(1);
    }

    @Test
    void theFormattedClockIsPaddedAndSecondless() {
        assertThat(SimulationClock.formatTimeOfDay(0)).isEqualTo("00:00");
        assertThat(SimulationClock.formatTimeOfDay(1)).isEqualTo("00:05");
        assertThat(SimulationClock.formatTimeOfDay(72)).isEqualTo("06:00");
        assertThat(SimulationClock.formatTimeOfDay(287)).isEqualTo("23:55");
    }

    @Test
    void dayZeroIsAMondaySoTheWeekendLinesUp() {
        assertThat(SimulationClock.dayOfWeek(0)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(SimulationClock.dayOfWeek(5 * SimulationClock.TICKS_PER_DAY)).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(SimulationClock.dayOfWeek(6 * SimulationClock.TICKS_PER_DAY)).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(SimulationClock.dayOfWeek(7 * SimulationClock.TICKS_PER_DAY)).isEqualTo(DayOfWeek.MONDAY);
    }
}
