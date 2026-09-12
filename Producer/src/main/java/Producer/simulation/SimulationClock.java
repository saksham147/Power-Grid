package Producer.simulation;

import java.time.Duration;
import java.time.LocalTime;

/**
 * The one place the tick/time relationship is stated.
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
 * {@link #TICKS_PER_DAY} is not an arbitrary pace setting. The solar strategy
 * divides the tick
 * number by it to get a position in the day, so it is what makes sunrise fall on
 * tick 72 and sunset
 * on tick 216. The dashboard clock reads the same number, which is why it lives
 * here rather than
 * privately inside either one -- two copies that must agree would be a drift
 * waiting to happen.
 *
 * <p>
 * {@link #REAL_TIME_PER_TICK} is a constant rather than a setting for the same
 * reason: a
 * configured value could silently break the 1 s = 1 min contract while the solar
 * curve carried on
 * assuming 288 ticks a day, and nothing would report the mismatch.
 */
public final class SimulationClock {

    /** Ticks in one simulated day. Sunrise is tick 72, solar noon 144, sunset 216. */
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

    /**
     * Time of day as {@code "HH:mm"}.
     *
     * <p>
     * Formatted here rather than left as a {@link LocalTime} for the serialiser to
     * render. Jackson
     * writes one as {@code "12:00:00"}, and the seconds are noise on a clock whose
     * smallest step is
     * five minutes -- and pinning the shape with {@code @JsonFormat} would mean
     * reaching for a
     * Jackson 2 annotation from a Jackson 3 runtime.
     */
    public static String formatTimeOfDay(long tick) {
        LocalTime time = timeOfDay(tick);
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }

    /** Simulated days elapsed; tick 0 through 287 are day 0. */
    public static long dayNumber(long tick) {
        return Math.floorDiv(tick, TICKS_PER_DAY);
    }

    /**
     * Energy a plant produces in one tick.
     *
     * <p>
     * Power times time: MW held for five simulated minutes is MW / 12 megawatt
     * hours. Note that
     * this is simulated time, so a tick's energy does not depend on how fast the
     * loop is running.
     */
    public static double energyMwh(double outputMw) {
        return outputMw * SIMULATED_MINUTES_PER_TICK / 60.0;
    }
}
