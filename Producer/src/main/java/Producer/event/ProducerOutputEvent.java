package Producer.event;

import java.time.Instant;

/**
 * One plant's contribution for one tick, published to {@code producer.output}
 * keyed by
 * {@code producerId} so a given plant's history stays ordered on a single
 * partition.
 */
public record ProducerOutputEvent(Long producerId, long tickNumber, double outputMw, Instant timestamp) {}
