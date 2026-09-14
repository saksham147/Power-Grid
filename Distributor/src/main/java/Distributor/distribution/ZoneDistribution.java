package Distributor.distribution;

import java.time.Instant;

/**
 * One zone's merged supply-and-demand picture for a tick -- the domain counterpart of
 * {@link Distributor.event.ZoneBalanceEvent}. See that class for what each figure means.
 */
public record ZoneDistribution(
        String zoneId,
        String zoneName,
        long tick,
        double demandKw,
        double suppliedKw,
        double balanceKw,
        Instant timestamp) {
}
