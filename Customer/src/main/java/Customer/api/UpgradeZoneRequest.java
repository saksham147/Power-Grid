package Customer.api;

import Customer.domain.Zone;
import jakarta.validation.constraints.NotBlank;

/**
 * New details for an existing zone -- just a rename now that a zone carries nothing else. There
 * is no {@code zoneId} here -- the path variable is the identity being upgraded, matching
 * {@code Producer.api.dto.UpgradePlantRequest}.
 */
public record UpgradeZoneRequest(@NotBlank String name) {

    public Zone toZone(String zoneId) {
        return new Zone(zoneId, name);
    }
}
