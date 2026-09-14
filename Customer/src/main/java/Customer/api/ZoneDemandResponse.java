package Customer.api;

/**
 * One configured zone, joined against its latest demand.
 *
 * @param zoneId    stable identifier, e.g. {@code "Z-NORTH"}
 * @param name      display name
 * @param customers population the zone represents
 * @param profile   {@code Customer.domain.DemandProfile} name -- a string here so the API does not
 *                  couple a client to the enum's Java type
 * @param demandKw  latest reading; 0 if no tick has run yet, or if this zone was added to
 *                  configuration after the store's last write
 */
public record ZoneDemandResponse(String zoneId, String name, long customers, String profile, double demandKw) {
}
