package Customer.domain;

/**
 * One zone's aggregated demand for one tick -- the sum of its units' own demand.
 *
 * <p>
 * Carries the zone identity alongside the figure so neither Redis nor Kafka has to look a zone up
 * to describe it. Tick and simulated time are deliberately absent: they are the same for every
 * zone in a tick, so they belong to the tick, not repeated on each zone.
 *
 * @param zoneId    stable identifier, and the Kafka partition key
 * @param name      human label
 * @param unitCount how many units this figure sums over
 * @param demandKw  total demand for the zone, in kW
 */
public record ZoneDemand(String zoneId, String name, long unitCount, double demandKw) {
}
