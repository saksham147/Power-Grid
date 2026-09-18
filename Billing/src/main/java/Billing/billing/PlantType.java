package Billing.billing;

/**
 * The kinds of generation this simulation models -- a structural, not a Java, contract: this
 * deliberately mirrors {@code Producer.model.PlantType} name-for-name rather than sharing a class
 * file, the same reason {@code Billing.event.ZoneDemandEvent} mirrors Customer's event. Needed here
 * so a plant's purchase price can be computed from the same type Producer will build.
 */
public enum PlantType {
    THERMAL,
    SOLAR,
    WIND
}
