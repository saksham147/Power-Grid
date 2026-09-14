package Distributor.distribution;

/**
 * Outbound port: publishes a merged balance to whatever is downstream.
 *
 * <p>
 * Implementations must not throw -- {@link DistributionService} treats a failure here as
 * independent of {@link DistributionLogger}'s outcome, not a reason to skip it.
 */
public interface BalancePublisher {

    void publish(ZoneDistribution distribution);
}
