package Distributor.distribution;

/**
 * Outbound port: records a merged balance as a permanent log entry.
 *
 * <p>
 * Implementations must not throw -- {@link DistributionService} treats a failure here as
 * independent of {@link BalancePublisher}'s outcome, not a reason to skip it.
 */
public interface DistributionLogger {

    void log(ZoneDistribution distribution);
}
