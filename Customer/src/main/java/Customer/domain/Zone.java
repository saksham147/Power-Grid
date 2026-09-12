package Customer.domain;

/**
 * A population of customers sharing a supply area and a demand shape.
 *
 * <p>
 * {@code customers} is a population size, not a collection size. No customer is
 * ever instantiated
 * -- see {@link DemandModel} for why, and for what that costs.
 *
 * @param zoneId              stable identifier, used as the Kafka partition key
 * @param name                human label carried through to events and Redis
 * @param customers           how many customers this zone supplies
 * @param profile             how one customer's demand moves over a day and week
 * @param baseKwPerCustomer   average demand per customer, in kW, across a weekday
 * @param customerVariability spread of one customer's demand about its own mean,
 *                            as a fraction. Independent between customers, so its
 *                            effect on the zone total falls away as the
 *                            population grows.
 * @param zoneVariability     spread shared by the whole zone -- weather, daylight,
 *                            a televised event. Correlated, so it does not shrink
 *                            with population and is what keeps a large zone's
 *                            demand visibly alive.
 */
public record Zone(
        String zoneId,
        String name,
        long customers,
        DemandProfile profile,
        double baseKwPerCustomer,
        double customerVariability,
        double zoneVariability) {

    public Zone {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (customers <= 0) {
            throw new IllegalArgumentException("zone " + zoneId + " must have at least one customer");
        }
        if (baseKwPerCustomer <= 0) {
            throw new IllegalArgumentException("zone " + zoneId + " must have a positive baseKwPerCustomer");
        }
        if (customerVariability < 0 || zoneVariability < 0) {
            throw new IllegalArgumentException("zone " + zoneId + " variabilities must not be negative");
        }
        if (profile == null) {
            throw new IllegalArgumentException("zone " + zoneId + " must have a profile");
        }
    }
}
