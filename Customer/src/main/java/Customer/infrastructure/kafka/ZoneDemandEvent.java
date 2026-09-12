package Customer.infrastructure.kafka;

import java.time.Instant;

/**
 * A zone's aggregated demand, as published to {@code customer.demand}.
 *
 * <p>
 * Only zone-level aggregates leave this service; no individual customer is ever
 * represented on
 * the wire, and none exists to represent.
 *
 * <p>
 * Separate from the domain's {@code ZoneDemand} on purpose. This is a wire
 * contract that other
 * services compile against, so it carries the tick context each record needs to
 * stand alone, and
 * it can evolve for consumers without dragging the domain along.
 *
 * @param zoneId        partition key -- one zone's history stays ordered
 * @param zoneName      human label, so a consumer need not resolve zones itself
 * @param tick          simulation tick this covers
 * @param simulatedTime simulated time of day, {@code "HH:mm"}
 * @param customers     population the figure aggregates
 * @param demandKw      total demand for the zone, in kW
 * @param timestamp     wall-clock instant of the tick, shared across its records
 */
public record ZoneDemandEvent(
        String zoneId,
        String zoneName,
        long tick,
        String simulatedTime,
        long customers,
        double demandKw,
        Instant timestamp) {
}
