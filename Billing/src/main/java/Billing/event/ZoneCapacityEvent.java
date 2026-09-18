package Billing.event;

import java.time.Instant;

/**
 * A zone's power-capacity configuration, consumed from {@code distributor.zone-capacity}.
 *
 * <p>
 * A structural, not a Java, contract: this deliberately mirrors {@code Distributor.event.
 * ZoneCapacityEvent} field-for-field rather than sharing a class file, the same convention
 * {@code Billing.event.ZoneDemandEvent} already follows for Customer's event.
 *
 * @param zoneId     stable identifier, and the partition key
 * @param zoneName   human label
 * @param capacityKw the zone's assigned capacity in kW, or {@code null} if the capacity was
 *                    removed -- see {@link Billing.billing.ZoneCapacityCache}
 * @param timestamp  wall-clock instant of the change
 */
public record ZoneCapacityEvent(
        String zoneId,
        String zoneName,
        Double capacityKw,
        Instant timestamp) {
}
