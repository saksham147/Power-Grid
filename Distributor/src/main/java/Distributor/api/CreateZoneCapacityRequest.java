package Distributor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * @param zoneId     stable identifier -- should match the zone id Customer uses, so the capacity
 *                    actually lines up with the demand it is meant to cap; Distributor has no way
 *                    to verify that against Customer, the same trust boundary every other
 *                    cross-service id in this project already accepts
 * @param zoneName    display name
 * @param capacityKw  the power capacity assigned to this zone, in kW
 */
public record CreateZoneCapacityRequest(
        @NotBlank String zoneId,
        @NotBlank String zoneName,
        @Positive double capacityKw) {
}
