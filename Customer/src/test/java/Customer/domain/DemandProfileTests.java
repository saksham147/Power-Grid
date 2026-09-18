package Customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Each profile has to actually have the shape its name claims. */
class DemandProfileTests {

    private static final DayOfWeek WEEKDAY = DayOfWeek.WEDNESDAY;
    private static final DayOfWeek WEEKEND = DayOfWeek.SUNDAY;

    @Test
    void residentialPeaksInTheEvening() {
        double evening = DemandProfile.RESIDENTIAL.factorAt(LocalTime.of(19, 0), WEEKDAY);
        double night = DemandProfile.RESIDENTIAL.factorAt(LocalTime.of(3, 0), WEEKDAY);
        double midday = DemandProfile.RESIDENTIAL.factorAt(LocalTime.of(13, 0), WEEKDAY);

        assertThat(evening).isGreaterThan(midday).isGreaterThan(night);
        // People are home more at weekends, so the curve lifts rather than collapsing.
        assertThat(DemandProfile.RESIDENTIAL.factorAt(LocalTime.of(19, 0), WEEKEND)).isGreaterThan(evening);
    }

    @Test
    void commercialTracksBusinessHoursAndEmptiesAtWeekends() {
        double working = DemandProfile.COMMERCIAL.factorAt(LocalTime.of(11, 0), WEEKDAY);
        double overnight = DemandProfile.COMMERCIAL.factorAt(LocalTime.of(3, 0), WEEKDAY);

        assertThat(working).isGreaterThan(overnight * 5);
        assertThat(DemandProfile.COMMERCIAL.factorAt(LocalTime.of(11, 0), WEEKEND))
                .isLessThan(working / 2);
    }

    @Test
    void industrialIsNearlyFlat() {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int hour = 0; hour < 24; hour++) {
            double f = DemandProfile.INDUSTRIAL.factorAt(LocalTime.of(hour, 0), WEEKDAY);
            min = Math.min(min, f);
            max = Math.max(max, f);
        }
        // A continuous process, not an office.
        assertThat(max / min).isLessThan(1.3);
    }

    @Test
    void govTracksOfficeHoursAndIsAlmostClosedAtWeekends() {
        double working = DemandProfile.GOV.factorAt(LocalTime.of(11, 0), WEEKDAY);
        double overnight = DemandProfile.GOV.factorAt(LocalTime.of(3, 0), WEEKDAY);

        assertThat(working).isGreaterThan(overnight * 5);
        // Steadier at weekends than a shop (COMMERCIAL), but still almost fully closed.
        assertThat(DemandProfile.GOV.factorAt(LocalTime.of(11, 0), WEEKEND)).isLessThan(working / 4);
    }

    @ParameterizedTest
    @EnumSource(DemandProfile.class)
    void everyFactorIsPositiveAtEveryMinuteOfTheWeek(DemandProfile profile) {
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int minute = 0; minute < 24 * 60; minute += 5) {
                double factor = profile.factorAt(LocalTime.MIDNIGHT.plusMinutes(minute), day);
                assertThat(factor).as("%s at %s on %s", profile, minute, day).isPositive();
            }
        }
    }

    /** Interpolation must wrap 23:00 -> 00:00 rather than stepping off the end of the table. */
    @ParameterizedTest
    @EnumSource(DemandProfile.class)
    void theCurveIsContinuousAcrossMidnight(DemandProfile profile) {
        double lastOfDay = profile.factorAt(LocalTime.of(23, 55), WEEKDAY);
        double firstOfDay = profile.factorAt(LocalTime.of(0, 0), WEEKDAY);

        assertThat(Math.abs(lastOfDay - firstOfDay)).isLessThan(0.1);
    }
}
