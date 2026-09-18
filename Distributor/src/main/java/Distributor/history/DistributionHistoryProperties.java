package Distributor.history;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Distribution history settings, bound from {@code distributor.history.*}.
 *
 * @param rawRetentionDays how many <em>simulated</em> days of per-tick rows stay raw before being
 *                          rolled up -- not wall-clock time, since a paused or restarted simulation
 *                          must not change how much simulated history survives
 * @param rollupInterval   how often the rollup runs, in real time; each run moves only what has
 *                          aged out since the last
 * @param rollupEnabled    schedule the rollup at startup. Tests turn this off and call it directly.
 */
@ConfigurationProperties("distributor.history")
public record DistributionHistoryProperties(
        @DefaultValue("15") long rawRetentionDays,
        // ISO-8601, not "10m": this value is also fed straight into @Scheduled's fixedDelayString
        // elsewhere, whose Duration parsing is stricter than @ConfigurationProperties binding.
        @DefaultValue("PT10M") Duration rollupInterval,
        @DefaultValue("true") boolean rollupEnabled) {
}
