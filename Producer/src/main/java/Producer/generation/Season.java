package Producer.generation;

import Producer.simulation.SimulationClock;

/**
 * The simulated year's season, purely a function of the tick number -- same "deterministic,
 * replayable" contract every other time-derived value in this project follows (see {@link
 * SimulationClock}). Multiplies on top of the existing daily/weather capacity factor in {@link
 * SolarGenerationStrategy}/{@link WindGenerationStrategy}; it does not replace either curve, it
 * scales it.
 *
 * <p>
 * {@link #DAYS_PER_SEASON} is chosen so a full year (4 seasons) cycles in 88 simulated days -- at
 * 24 real minutes per simulated day, that is a season change roughly every 8-9 real hours, close
 * enough to observe within one real day of the simulation running rather than requiring a
 * multi-day wait.
 */
public enum Season {

    // Multipliers are deliberately asymmetric between solar and wind: winter has weak sun but
    // often stronger, more consistent wind, and summer the reverse -- the same real-world
    // seasonal complementarity that makes a mixed renewable fleet more reliable than either
    // technology alone.
    SPRING(1.00, 1.05),
    SUMMER(1.15, 0.85),
    AUTUMN(0.90, 1.10),
    WINTER(0.70, 1.20);

    /** Simulated days in one season; 4 seasons make an 88-day simulated year. */
    static final long DAYS_PER_SEASON = 22;

    private final double solarFactor;
    private final double windFactor;

    Season(double solarFactor, double windFactor) {
        this.solarFactor = solarFactor;
        this.windFactor = windFactor;
    }

    public double solarFactor() {
        return solarFactor;
    }

    public double windFactor() {
        return windFactor;
    }

    public static Season of(long tickNumber) {
        long day = SimulationClock.dayNumber(tickNumber);
        int index = (int) Math.floorMod(Math.floorDiv(day, DAYS_PER_SEASON), values().length);
        return values()[index];
    }
}
