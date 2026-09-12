package Customer.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Converts a tick number into simulated time.
 *
 * <h2>The contract</h2>
 *
 * <pre>
 *   a tick covers   simulatedMinutesPerTick   of simulated time
 *   1 real second = 1 simulated minute
 *   =&gt; a tick fires every simulatedMinutesPerTick real seconds
 * </pre>
 *
 * <p>
 * An instance rather than a holder of constants, because the tick size is
 * configurable. The real
 * time between ticks is derived here rather than configured separately, so the
 * two can never be
 * set to contradict each other.
 *
 * <p>
 * Day 0 is a Monday, so weekday and weekend behaviour is reachable from tick 0
 * without any wall
 * clock being involved. Nothing in this class reads the system time: simulated
 * time is a pure
 * function of the tick number, which is what makes a run reproducible.
 */
public record SimulationClock(int simulatedMinutesPerTick, int realSecondsPerSimulatedMinute) {

    private static final int MINUTES_PER_DAY = 24 * 60;

    public SimulationClock {
        if (simulatedMinutesPerTick <= 0 || realSecondsPerSimulatedMinute <= 0) {
            throw new IllegalArgumentException(
                    "simulatedMinutesPerTick and realSecondsPerSimulatedMinute must both be positive");
        }
        if (MINUTES_PER_DAY % simulatedMinutesPerTick != 0) {
            // Otherwise a day is a non-integer number of ticks and the daily profile drifts
            // a little further out of phase every simulated day.
            throw new IllegalArgumentException(
                    "simulatedMinutesPerTick must divide 1440 exactly, got " + simulatedMinutesPerTick);
        }
    }

    /** Whole ticks in one simulated day. */
    public long ticksPerDay() {
        return MINUTES_PER_DAY / simulatedMinutesPerTick;
    }

    /** Real seconds between ticks -- the pace the scheduler runs at. */
    public long realSecondsPerTick() {
        return (long) simulatedMinutesPerTick * realSecondsPerSimulatedMinute;
    }

    public LocalTime timeOfDay(long tick) {
        return LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(tick, ticksPerDay()) * simulatedMinutesPerTick);
    }

    /** Simulated days elapsed; the first full day of ticks is day 0. */
    public long dayNumber(long tick) {
        return Math.floorDiv(tick, ticksPerDay());
    }

    /** Day 0 is a Monday, so a weekend is reached seven simulated days in. */
    public DayOfWeek dayOfWeek(long tick) {
        return DayOfWeek.of((int) Math.floorMod(dayNumber(tick), 7) + 1);
    }

    /**
     * Time of day as {@code "HH:mm"}. Formatted here rather than left to a
     * serialiser, which
     * would render a {@link LocalTime} with seconds that this clock does not have.
     */
    public String formatTimeOfDay(long tick) {
        LocalTime time = timeOfDay(tick);
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }
}
