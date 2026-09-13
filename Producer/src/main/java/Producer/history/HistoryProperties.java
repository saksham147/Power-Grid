package Producer.history;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Generation history settings, bound from {@code producer.history.*}.
 *
 * @param rawRetention   how long per-tick rows stay raw before being rolled up
 * @param rollupInterval how often the rollup runs; each run moves only what has
 *                       aged out since the last
 * @param rollupEnabled  schedule the rollup at startup. Tests turn this off and
 *                       call it directly.
 */
@ConfigurationProperties("producer.history")
public record HistoryProperties(
        @DefaultValue("24h") Duration rawRetention,
        @DefaultValue("10m") Duration rollupInterval,
        @DefaultValue("true") boolean rollupEnabled) {
}
