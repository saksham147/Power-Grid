package Customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** The closed-form formula: a unit's demand is its capacity times its type's factor, nothing more. */
class DemandModelTests {

    private static final DayOfWeek WEEKDAY = DayOfWeek.WEDNESDAY;
    private static final LocalTime EVENING = LocalTime.of(19, 0);

    private static ConsumerUnit unit(DemandProfile type, double capacityKw) {
        return new ConsumerUnit("U-1", "Z-1", "Unit", type, capacityKw);
    }

    @Test
    void theSameUnitAndMomentAlwaysProduceTheSameFigure() {
        ConsumerUnit u = unit(DemandProfile.RESIDENTIAL, 800);

        assertThat(DemandModel.demandKw(u, EVENING, WEEKDAY)).isEqualTo(DemandModel.demandKw(u, EVENING, WEEKDAY));
    }

    @Test
    void demandIsCapacityTimesTheTypesFactorAtThatMoment() {
        ConsumerUnit u = unit(DemandProfile.RESIDENTIAL, 800);

        double expected = 800 * DemandProfile.RESIDENTIAL.factorAt(EVENING, WEEKDAY);

        assertThat(DemandModel.demandKw(u, EVENING, WEEKDAY)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void demandScalesLinearlyWithCapacity() {
        ConsumerUnit small = unit(DemandProfile.INDUSTRIAL, 500);
        ConsumerUnit big = unit(DemandProfile.INDUSTRIAL, 1000);

        double smallKw = DemandModel.demandKw(small, EVENING, WEEKDAY);
        double bigKw = DemandModel.demandKw(big, EVENING, WEEKDAY);

        assertThat(bigKw).isCloseTo(smallKw * 2, within(1e-9));
    }

    @Test
    void demandIsNeverNegative() {
        for (DemandProfile type : DemandProfile.values()) {
            ConsumerUnit u = unit(type, 100);
            for (int hour = 0; hour < 24; hour++) {
                assertThat(DemandModel.demandKw(u, LocalTime.of(hour, 0), WEEKDAY)).isNotNegative();
            }
        }
    }

    @Test
    void residentialDemandFollowsTheProfileAcrossTheDay() {
        ConsumerUnit u = unit(DemandProfile.RESIDENTIAL, 800);

        double evening = DemandModel.demandKw(u, EVENING, WEEKDAY);
        double night = DemandModel.demandKw(u, LocalTime.of(3, 0), WEEKDAY);

        assertThat(evening).isGreaterThan(night * 2);
    }

    /** Two units with the same capacity but different types must not report the same demand --
     *  the whole point of "type" being part of the formula, not just a label. */
    @Test
    void typeChangesTheOutcomeForTheSameCapacity() {
        ConsumerUnit residential = unit(DemandProfile.RESIDENTIAL, 800);
        ConsumerUnit commercial = unit(DemandProfile.COMMERCIAL, 800);

        // Mid-afternoon: commercial is near its plateau, residential is in its daytime trough.
        LocalTime midAfternoon = LocalTime.of(14, 0);
        assertThat(DemandModel.demandKw(commercial, midAfternoon, WEEKDAY))
                .isGreaterThan(DemandModel.demandKw(residential, midAfternoon, WEEKDAY));
    }
}
