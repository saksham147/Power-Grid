package Customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * The closed-form aggregation, which is the load-bearing claim of this service:
 * a zone's demand
 * is computed, not summed over customers.
 */
class DemandModelTests {

    private static final DayOfWeek WEEKDAY = DayOfWeek.WEDNESDAY;
    private static final LocalTime EVENING = LocalTime.of(19, 0);

    private static Zone residential(long customers, double customerVariability, double zoneVariability) {
        return new Zone("Z-" + customers, "Zone", customers, DemandProfile.RESIDENTIAL,
                1.1, customerVariability, zoneVariability);
    }

    private static double swingFraction(Zone zone, int ticks) {
        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;
        for (long tick = 0; tick < ticks; tick++) {
            double kw = DemandModel.demandKw(zone, EVENING, WEEKDAY, tick);
            min = Math.min(min, kw);
            max = Math.max(max, kw);
            sum += kw;
        }
        return (max - min) / (sum / ticks);
    }

    @Test
    void theSameZoneAndTickAlwaysProduceTheSameFigure() {
        Zone zone = residential(250_000, 0.35, 0.06);

        assertThat(DemandModel.demandKw(zone, EVENING, WEEKDAY, 4242))
                .isEqualTo(DemandModel.demandKw(zone, EVENING, WEEKDAY, 4242));
    }

    @Test
    void theMeanTracksPopulationTimesPerCustomerDemand() {
        Zone zone = residential(250_000, 0.35, 0.06);

        double sum = 0;
        int ticks = 2000;
        for (long tick = 0; tick < ticks; tick++) {
            sum += DemandModel.demandKw(zone, EVENING, WEEKDAY, tick);
        }
        double observed = sum / ticks;
        double expected = 250_000 * 1.1 * DemandProfile.RESIDENTIAL.factorAt(EVENING, WEEKDAY);

        assertThat(observed).isCloseTo(expected, org.assertj.core.data.Percentage.withPercentage(3));
    }

    /**
     * The Central Limit Theorem in a test: independent per-customer noise contributes
     * {@code sigma*sqrt(N)} to a total of {@code N*mean}, so its share must fall away
     * as the
     * population grows. This is exactly why a second, correlated variability exists.
     */
    @Test
    void independentNoiseFadesAsThePopulationGrows() {
        double small = swingFraction(residential(100, 0.35, 0.0), 500);
        double medium = swingFraction(residential(10_000, 0.35, 0.0), 500);
        double large = swingFraction(residential(5_000_000L, 0.35, 0.0), 500);

        assertThat(small).isGreaterThan(medium);
        assertThat(medium).isGreaterThan(large);
        // A five-million-customer zone would be a flat line on independent noise alone.
        assertThat(large).isLessThan(0.005);
    }

    /** Correlated variability scales the mean, so it survives any population size. */
    @Test
    void correlatedNoiseKeepsALargeZoneAlive() {
        double large = swingFraction(residential(5_000_000L, 0.35, 0.06), 500);

        assertThat(large).isGreaterThan(0.05);
    }

    @Test
    void demandIsNeverNegative() {
        // A tiny population with an absurd spread is the only way to drive the sample
        // below zero, and it still must not report negative consumption.
        Zone volatileZone = residential(2, 5.0, 0.5);

        for (long tick = 0; tick < 5000; tick++) {
            assertThat(DemandModel.demandKw(volatileZone, EVENING, WEEKDAY, tick)).isNotNegative();
        }
    }

    @Test
    void demandFollowsTheProfileAcrossTheDay() {
        Zone zone = residential(250_000, 0.35, 0.0);

        double evening = DemandModel.demandKw(zone, EVENING, WEEKDAY, 100);
        double night = DemandModel.demandKw(zone, LocalTime.of(3, 0), WEEKDAY, 100);

        assertThat(evening).isGreaterThan(night * 2);
    }

    /**
     * The whole design in one assertion: cost is independent of population, because no
     * customer
     * is ever visited.
     */
    @Test
    void costDoesNotGrowWithPopulation() {
        Zone tiny = residential(20, 0.35, 0.06);
        Zone huge = residential(5_000_000L, 0.35, 0.06);

        assertThat(timeFor(tiny)).isCloseTo(timeFor(huge), org.assertj.core.data.Percentage.withPercentage(400));
    }

    private static long timeFor(Zone zone) {
        // Warm up, so the measurement is of the model rather than of class loading.
        for (long tick = 0; tick < 20_000; tick++) {
            DemandModel.demandKw(zone, EVENING, WEEKDAY, tick);
        }
        long start = System.nanoTime();
        for (long tick = 0; tick < 100_000; tick++) {
            DemandModel.demandKw(zone, EVENING, WEEKDAY, tick);
        }
        return System.nanoTime() - start;
    }
}
