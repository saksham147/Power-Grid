package Billing.event;

import java.time.Instant;

import Billing.billing.PlantType;

/**
 * A plant roster change, consumed from {@code producer.plants}.
 *
 * <p>
 * A structural, not a Java, contract: this deliberately mirrors {@code Producer.event.
 * PlantRosterEvent} field-for-field rather than sharing a class file, the same convention
 * {@code Billing.event.ZoneCapacityEvent} already follows for Distributor's event.
 *
 * @param removed true when the plant was deleted -- see {@link Billing.billing.PlantRosterCache}
 */
public record PlantRosterEvent(
        Long plantId,
        PlantType type,
        double capacityMw,
        boolean active,
        boolean removed,
        Instant timestamp) {
}
