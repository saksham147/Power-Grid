package Producer.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * The tick/time contract.
 *
 * <p>
 * These are the numbers the solar curve and the dashboard clock both depend on,
 * so they are
 * pinned here rather than left implicit in whichever file happens to read them.
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
        // Sunrise and sunset in SolarGenerationStrategy are day fractions 0.25 and 0.75.
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

    /** Power times simulated time: 600 MW held for five minutes is 50 MWh. */
    @Test
    void energyIsPowerOverTheTicksSimulatedDuration() {
        assertThat(SimulationClock.energyMwh(600)).isEqualTo(50.0);
        assertThat(SimulationClock.energyMwh(0)).isZero();
    }

    /**
     * A full simulated day at a steady 600 MW is 14.4 GWh, which is 600 MW times 24
     * h. If the
     * per-tick figure and the tick count ever disagree, this is what catches it.
     */
    @Test
    void aFullDayOfTicksAccumulatesTheRightEnergy() {
        double total = SimulationClock.TICKS_PER_DAY * SimulationClock.energyMwh(600);
        assertThat(total).isEqualTo(600.0 * 24);
    }
}
