package Customer.api;

import Customer.domain.ConsumerUnit;
import Customer.domain.DemandProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * New details for an existing unit, including which zone it belongs to -- unlike a plant's type,
 * a unit's type is only ever a lookup into {@link DemandProfile}'s curves, not a selector for a
 * different simulation strategy, so there is no reason to freeze it (or the zone assignment)
 * against an edit the way {@code Producer.api.dto.UpgradePlantRequest} freezes plant type.
 */
public record UpgradeUnitRequest(
        @NotBlank String zoneId,
        @NotBlank String name,
        @NotNull DemandProfile type,
        @Positive double capacityKw) {

    public ConsumerUnit toUnit(String unitId) {
        return new ConsumerUnit(unitId, zoneId, name, type, capacityKw);
    }
}
