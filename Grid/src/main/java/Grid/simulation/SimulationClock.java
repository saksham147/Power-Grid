package Grid.simulation;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

/**
 * The one place the tick/time relationship is defined for the whole system. Grid is now the single
 * owner of simulation time, so this -- not Producer's or Customer's own copy -- is the canonical
 * definition; the other services' copies exist only because there is no shared library module
 * between independently deployable services, and must be kept numerically identical to this one.
 *
 * <h2>The contract</h2>
 *
 * <pre>
 *   1 simulated day      = 288 ticks
 *   24 h / 288           = 5 simulated minutes per tick
 *   1 real second        = 1 simulated minute
 *   =&gt; a tick every 5 real seconds, and a simulated day every 24 real minutes
 * </pre>
 *
 * <p>
 * A constant rather than a setting: Producer's solar strategy divides the tick number by
 * {@link #TICKS_PER_DAY} to get a position in the day, so a configured value that drifted from this
 * would silently break sunrise/sunset for every consumer with nothing to report the mismatch.
 */
public final class SimulationClock {

    /** Ticks in one simulated day. */
    public static final long TICKS_PER_DAY = 288;

    /** 24 h / 288. */
    public static final long SIMULATED_MINUTES_PER_TICK = (24 * 60) / TICKS_PER_DAY;

    /** One real second per simulated minute. */
    public static final Duration REAL_TIME_PER_TICK = Duration.ofSeconds(SIMULATED_MINUTES_PER_TICK);

    private SimulationClock() {
    }

    /** Time of day the given tick represents, wrapping at midnight. */
    public static LocalTime timeOfDay(long tick) {
        return LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(tick, TICKS_PER_DAY) * SIMULATED_MINUTES_PER_TICK);
    }

    /** Time of day as {@code "HH:mm"}. */
    public static String formatTimeOfDay(long tick) {
        LocalTime time = timeOfDay(tick);
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }

    /** Simulated days elapsed; tick 0 through 287 are day 0. */
    public static long dayNumber(long tick) {
        return Math.floorDiv(tick, TICKS_PER_DAY);
    }

    /** Day 0 is a Monday, so the week -- and the weekend -- lines up the same way for every consumer. */
    public static DayOfWeek dayOfWeek(long tick) {
        return DayOfWeek.of((int) Math.floorMod(dayNumber(tick), 7) + 1);
    }
}
