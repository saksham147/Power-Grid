package Distributor.distribution;

import Distributor.event.ZoneCapacityEvent;

/**
 * Outbound port: broadcasts a zone's capacity configuration to whatever is downstream.
 *
 * <p>
 * Implementations must not throw -- {@link ZoneCapacityService} treats a publish failure as
 * independent of the JPA write's own outcome, the same shape {@link BalancePublisher} already
 * documents for {@link DistributionService}.
 */
public interface ZoneCapacityPublisher {

    void publish(ZoneCapacityEvent event);
}
