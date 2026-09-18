package Producer.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import Producer.simulation.SimulationClock;

/** The pure tick-to-season mapping: 22 simulated days per season, cycling every 4 seasons. */
class SeasonTests {

    private static final long TICKS_PER_DAY = SimulationClock.TICKS_PER_DAY;

    @Test
    void dayZeroIsSpring() {
        assertThat(Season.of(0)).isEqualTo(Season.SPRING);
    }

    @Test
    void theSeasonAdvancesEveryTwentyTwoSimulatedDays() {
        long summerStartTick = 22 * TICKS_PER_DAY;
        long autumnStartTick = 44 * TICKS_PER_DAY;
        long winterStartTick = 66 * TICKS_PER_DAY;

        assertThat(Season.of(summerStartTick)).isEqualTo(Season.SUMMER);
        assertThat(Season.of(autumnStartTick)).isEqualTo(Season.AUTUMN);
        assertThat(Season.of(winterStartTick)).isEqualTo(Season.WINTER);
    }

    @Test
    void theYearWrapsBackToSpringAfterFourSeasons() {
        long yearLengthTicks = 88 * TICKS_PER_DAY;
        assertThat(Season.of(yearLengthTicks)).isEqualTo(Season.SPRING);
    }
}
