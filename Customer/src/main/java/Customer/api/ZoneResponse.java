package Customer.api;

import Customer.domain.Zone;

/** A configured zone, as returned by the management API -- no demand figure; see {@code /api/demand} for that. */
public record ZoneResponse(
        String zoneId,
        String name,
        long customers,
        String profile,
        double baseKwPerCustomer,
        double customerVariability,
        double zoneVariability) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.zoneId(), zone.name(), zone.customers(), zone.profile().name(),
                zone.baseKwPerCustomer(), zone.customerVariability(), zone.zoneVariability());
    }
}
