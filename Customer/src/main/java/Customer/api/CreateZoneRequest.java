package Customer.api;

import Customer.domain.DemandProfile;
import Customer.domain.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A new zone.
 *
 * <p>
 * Cross-field consistency (a positive population, a positive per-customer figure) is checked by
 * {@link #toZone()} via {@link Zone}'s own compact constructor, so create and any future update
 * reject exactly the same combinations.
 *
 * @param zoneId               stable identifier; also the Redis and Kafka partition key
 * @param name                 display name
 * @param customers            population this zone represents
 * @param profile              which demand shape this zone follows over a day and week
 * @param baseKwPerCustomer    average demand per customer, in kW, across a weekday
 * @param customerVariability  spread of one customer's demand about its own mean, as a fraction
 * @param zoneVariability      spread shared by the whole zone, as a fraction
 */
public record CreateZoneRequest(
        @NotBlank String zoneId,
        @NotBlank String name,
        @Positive long customers,
        @NotNull DemandProfile profile,
        @Positive double baseKwPerCustomer,
        @PositiveOrZero double customerVariability,
        @PositiveOrZero double zoneVariability) {

    public Zone toZone() {
        return new Zone(zoneId, name, customers, profile, baseKwPerCustomer, customerVariability, zoneVariability);
    }
}
