package Producer.api.dto;

import java.time.Duration;

/**
 * Body of a start request. Both fields are optional, so an empty POST starts
 * with the configured
 * defaults.
 *
 * <p>
 * {@code tickInterval} accepts either ISO-8601 ({@code "PT2S"}) or Spring's
 * short form
 * ({@code "2s"}). A non-positive interval is rejected by the runner rather than
 * by a constraint
 * here, so the same rule applies however a run is started.
 *
 * @param tickInterval       wall-clock gap between ticks, or null for the
 *                           configured default
 * @param frequencyDeviation deviation in Hz applied to every looped tick, or
 *                           null for the configured
 *                           default
 */
public record StartSimulationRequest(Duration tickInterval, Double frequencyDeviation) {
}
