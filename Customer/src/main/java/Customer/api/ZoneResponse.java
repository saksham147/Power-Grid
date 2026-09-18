package Customer.api;

import Customer.domain.Zone;

/** A configured zone, as returned by the management API -- no demand figure; see {@code /api/demand} for that. */
public record ZoneResponse(String zoneId, String name) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.zoneId(), zone.name());
    }
}
