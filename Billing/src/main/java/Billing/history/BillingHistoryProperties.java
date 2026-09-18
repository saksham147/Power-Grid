package Billing.history;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Billing history settings, bound from {@code billing.history.*}. See {@code
 * Distributor.history.DistributionHistoryProperties} for why retention is counted in simulated
 * days, not wall-clock time.
 *
 * @param rawRetentionDays how many simulated days of per-tick billing rows stay raw before being
 *                          rolled up
 * @param rollupInterval   how often the rollup runs, in real time; each run moves only what has
 *                          aged out since the last
 * @param rollupEnabled    schedule the rollup at startup. Tests turn this off and call it directly.
 */
@ConfigurationProperties("billing.history")
public record BillingHistoryProperties(
        @DefaultValue("15") long rawRetentionDays,
        // ISO-8601, not "10m": this value is also fed straight into @Scheduled's fixedDelayString
        // elsewhere, whose Duration parsing is stricter than @ConfigurationProperties binding.
        @DefaultValue("PT10M") Duration rollupInterval,
        @DefaultValue("true") boolean rollupEnabled) {
}
