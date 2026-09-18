package Distributor.event;

import java.time.Instant;

/**
 * A zone's aggregated demand for one tick, consumed from {@code customer.demand}.
 *
 * <p>
 * A structural, not a Java, contract: this deliberately mirrors {@code Customer.infrastructure.
 * kafka.ZoneDemandEvent} field-for-field rather than sharing a class file -- there is no shared
 * library module between the services, so field names and order here are the actual interface.
 *
 * @param zoneId        stable identifier, and the partition key
 * @param zoneName      human label
 * @param tick          simulation tick this covers
 * @param simulatedTime simulated time of day, {@code "HH:mm"}
 * @param units         how many houses, factories and commercial buildings the figure sums over
 * @param demandKw      total demand for the zone, in kW
 * @param timestamp     wall-clock instant of the tick, shared across a tick's records
 */
public record ZoneDemandEvent(
        String zoneId,
        String zoneName,
        long tick,
        String simulatedTime,
        long units,
        double demandKw,
        Instant timestamp) {
}
