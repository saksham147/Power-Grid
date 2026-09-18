package Distributor.distribution;

/** No capacity is assigned to this zone -- there is nothing to update or delete. */
public class ZoneCapacityNotFoundException extends RuntimeException {

    public ZoneCapacityNotFoundException(String zoneId) {
        super("No capacity assigned to zone " + zoneId);
    }
}
