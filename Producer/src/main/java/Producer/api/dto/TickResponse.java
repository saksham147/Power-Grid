package Producer.api.dto;

import java.time.Instant;
import java.util.List;

import Producer.event.ProducerOutputEvent;
import Producer.simulation.TickResult;

/**
 * What a tick did, as reported over HTTP.
 *
 * <p>
 * {@code outputs} are the events exactly as published to Kafka, so the response
 * shows what
 * downstream services will receive rather than a re-description of it.
 *
 * @param tickNumber         the tick number actually issued
 * @param frequencyDeviation deviation in Hz the tick ran with
 * @param timestamp          when the tick ran
 * @param eventsPublished    how many events reached the topic. Zero means no
 *                           active plants, not a
 *                           failure -- seed the fleet first
 * @param outputs            the published events
 */
public record TickResponse(
        long tickNumber,
        double frequencyDeviation,
        Instant timestamp,
        int eventsPublished,
        List<ProducerOutputEvent> outputs) {

    public static TickResponse from(TickResult result) {
        return new TickResponse(
                result.tickNumber(),
                result.frequencyDeviation(),
                result.timestamp(),
                result.events().size(),
                result.events());
    }
}
