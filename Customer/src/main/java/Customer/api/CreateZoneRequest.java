package Customer.api;

import Customer.domain.Zone;
import jakarta.validation.constraints.NotBlank;

/**
 * A new zone -- just an id and a name. A zone is a pure container for {@link
 * Customer.domain.ConsumerUnit}s now; see {@link Customer.domain.DemandModel} for where the
 * demand formula actually lives.
 *
 * @param zoneId stable identifier; also the Redis key and the Kafka partition key
 * @param name   display name
 */
public record CreateZoneRequest(
        @NotBlank String zoneId,
        @NotBlank String name) {

    public Zone toZone() {
        return new Zone(zoneId, name);
    }
}
