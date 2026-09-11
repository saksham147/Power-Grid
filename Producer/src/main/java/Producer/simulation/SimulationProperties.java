package Producer.simulation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Defaults for the API-driven simulation loop, bound from
 * {@code producer.simulation.*}.
 *
 * <p>
 * These are defaults, not fixed settings: a start request may override either per
 * run. They apply
 * to looped ticks only -- a manual tick carries its own deviation.
 *
 * <p>
 * The {@code @DefaultValue}s duplicate what application.yml sets on purpose, so
 * the service still
 * starts with a sane loop configuration if the block is ever removed from the
 * YAML.
 *
 * @param tickInterval       wall-clock gap between looped ticks
 * @param frequencyDeviation departure from nominal grid frequency in Hz applied
 *                           to every looped tick.
 *                           Negative means the grid is running slow, i.e. demand
 *                           is outrunning generation.
 */
@ConfigurationProperties("producer.simulation")
public record SimulationProperties(
        @DefaultValue("5s") Duration tickInterval,
        @DefaultValue("0.0") double frequencyDeviation) {
}
