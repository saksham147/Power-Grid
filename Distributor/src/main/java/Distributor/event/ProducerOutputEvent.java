package Distributor.event;

import java.time.Instant;

/**
 * One plant's contribution for one tick, consumed from {@code producer.output}.
 *
 * <p>
 * A structural, not a Java, contract: this deliberately mirrors {@code Producer.event.
 * ProducerOutputEvent} field-for-field rather than sharing a class file -- there is no shared
 * library module between the services, so field names and order here are the actual interface.
 *
 * @param producerId the plant that produced this
 * @param tickNumber simulation tick this covers
 * @param outputMw   the plant's output for that tick, in megawatts
 * @param timestamp  wall-clock instant of the tick
 */
public record ProducerOutputEvent(Long producerId, long tickNumber, double outputMw, Instant timestamp) {
}
