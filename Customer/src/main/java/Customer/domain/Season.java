package Customer.domain;

/**
 * The simulated year's season, on the demand side -- a structural sibling to {@code
 * Producer.generation.Season}, not a shared class (same "own copy, mirrored by name" convention
 * {@code Customer.infrastructure.kafka.ZoneDemandEvent} and its counterparts already follow for
 * cross-service concepts). Deliberately keyed on {@code dayNumber} rather than a tick number, so
 * it stays independent of whichever {@link SimulationClock} instance is configured -- the clock
 * already turns a tick into a day number, this only turns a day number into a season.
 *
 * <p>
 * Demand rises in both summer (cooling load) and winter (heating load) relative to spring/autumn,
 * the same real-world "shoulder season" shape utilities actually see -- complementary to, not
 * copied from, {@code Producer.generation.Season}'s renewable-output curve.
 */
public enum Season {

    SPRING(1.00),
    SUMMER(1.20),
    AUTUMN(0.95),
    WINTER(1.15);

    /** Simulated days in one season; 4 seasons make an 88-day simulated year -- matches
     *  {@code Producer.generation.Season} so both sides of the grid change season together. */
    static final long DAYS_PER_SEASON = 22;

    private final double demandFactor;

    Season(double demandFactor) {
        this.demandFactor = demandFactor;
    }

    public double demandFactor() {
        return demandFactor;
    }

    public static Season of(long dayNumber) {
        int index = (int) Math.floorMod(Math.floorDiv(dayNumber, DAYS_PER_SEASON), values().length);
        return values()[index];
    }
}
