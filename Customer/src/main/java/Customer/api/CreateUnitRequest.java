package Customer.api;

import Customer.domain.ConsumerUnit;
import Customer.domain.DemandProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A new house, factory or commercial building.
 *
 * @param unitId     stable identifier
 * @param zoneId     the zone this unit belongs to -- not checked against {@code GET /api/zones}
 *                   here, the same way {@code Distributor} tolerates a zone id it has never seen;
 *                   a unit for a zone created moments later is not an error
 * @param name       display name
 * @param type       which demand shape this unit follows -- residential, commercial or industrial
 * @param capacityKw this unit's rated demand, in kW, at a profile factor of 1.0
 */
public record CreateUnitRequest(
        @NotBlank String unitId,
        @NotBlank String zoneId,
        @NotBlank String name,
        @NotNull DemandProfile type,
        @Positive double capacityKw) {

    public ConsumerUnit toUnit() {
        return new ConsumerUnit(unitId, zoneId, name, type, capacityKw);
    }
}
