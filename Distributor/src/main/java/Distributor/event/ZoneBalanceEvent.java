package Distributor.event;

import java.time.Instant;

/**
 * One zone's merged supply-and-demand picture, published to {@code distributor.zone-balance}.
 *
 * <p>
 * The wire counterpart of {@link Distributor.distribution.ZoneDistribution}. Kept separate on
 * purpose, the same way Customer keeps {@code ZoneDemandEvent} apart from its domain
 * {@code ZoneDemand}: this is a contract other services (Billing, eventually) compile against, so
 * it can evolve for them without dragging the merge logic's own domain type along.
 *
 * @param zoneId      partition key -- one zone's history stays ordered
 * @param zoneName    human label, so a consumer need not resolve zones itself
 * @param tick        the tick this balance was computed for (the tick of the demand reading that
 *                    triggered it -- see {@link Distributor.distribution.DistributionService})
 * @param demandKw    the zone's demand, in kW
 * @param suppliedKw  generation allocated to the zone, in kW -- its share of total fleet output,
 *                    proportional to its share of total demand
 * @param balanceKw   {@code suppliedKw - demandKw}. Positive means the zone is allocated more than
 *                    it is asking for; negative means less
 * @param timestamp   wall-clock instant this balance was computed
 */
public record ZoneBalanceEvent(
        String zoneId,
        String zoneName,
        long tick,
        double demandKw,
        double suppliedKw,
        double balanceKw,
        Instant timestamp) {
}
