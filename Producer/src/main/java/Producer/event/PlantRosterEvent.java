package Producer.event;

import java.time.Instant;

import Producer.model.PlantType;

/**
 * Announces a change to the plant roster itself -- created, re-rated, activated/deactivated, or
 * deleted -- as opposed to {@link ProducerOutputEvent}, which reports one plant's output for one
 * tick and carries neither its type nor its capacity. Consumers that need to know what plants
 * exist (Billing's maintenance charge, for one) mirror this event rather than polling Producer's
 * REST API, matching how every other cross-service dependency in this project is Kafka, not REST.
 *
 * @param removed true when the plant was deleted; {@code type}/{@code capacityMw}/{@code active}
 *                are still populated from the plant's last known state so a consumer can log what
 *                was removed, but a mirror should drop the entry on {@code removed == true}
 */
public record PlantRosterEvent(
        Long plantId,
        PlantType type,
        double capacityMw,
        boolean active,
        boolean removed,
        Instant timestamp) {
}
