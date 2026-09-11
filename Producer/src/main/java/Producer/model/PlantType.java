package Producer.model;

/**
 * The kinds of generation this simulation models.
 *
 * <p>
 * The split that matters physically is dispatchable vs. non-dispatchable.
 * {@code THERMAL} is a
 * synchronous machine under governor control, so it responds to grid frequency.
 * {@code SOLAR} and
 * {@code WIND} are inverter-coupled and take whatever the weather gives them.
 */
public enum PlantType {
    THERMAL,
    SOLAR,
    WIND
}
