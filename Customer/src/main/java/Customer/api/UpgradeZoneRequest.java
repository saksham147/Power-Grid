package Customer.api;

import Customer.domain.DemandProfile;
import Customer.domain.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * New details for an existing zone. There is no {@code zoneId} here -- the path variable is the
 * identity being upgraded, matching {@code Producer.api.dto.UpgradePlantRequest}.
 *
 * <p>
 * All fields are required, which is why the endpoint is a PUT rather than a PATCH: a partial
 * upgrade would need per-field null handling to express something a second call could already say.
 * Cross-field consistency is checked by {@link #toZone} via {@link Zone}'s own compact constructor,
 * so an upgrade rejects exactly what {@link CreateZoneRequest} does.
 */
public record UpgradeZoneRequest(
        @NotBlank String name,
        @Positive long customers,
        @NotNull DemandProfile profile,
        @Positive double baseKwPerCustomer,
        @PositiveOrZero double customerVariability,
        @PositiveOrZero double zoneVariability) {

    public Zone toZone(String zoneId) {
        return new Zone(zoneId, name, customers, profile, baseKwPerCustomer, customerVariability, zoneVariability);
    }
}
