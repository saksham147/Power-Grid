package Distributor.event;

import java.time.Instant;

/**
 * Broadcasts a zone's power-capacity configuration to anything downstream that needs it -- Billing,
 * to apply an overage surcharge, without duplicating the capacity data entry itself (see
 * {@code Billing.event.ZoneCapacityEvent}, the local wire copy that mirrors this one field-for-field,
 * the same convention {@code ZoneDemandEvent} already follows across services).
 *
 * @param zoneId     stable identifier, and the partition key
 * @param zoneName   human label
 * @param capacityKw the zone's assigned capacity in kW, or {@code null} if the capacity was
 *                    removed -- a zone with no capacity is uncapped, billed at the normal rate only
 * @param timestamp  wall-clock instant of the change
 */
public record ZoneCapacityEvent(
        String zoneId,
        String zoneName,
        Double capacityKw,
        Instant timestamp) {
}
