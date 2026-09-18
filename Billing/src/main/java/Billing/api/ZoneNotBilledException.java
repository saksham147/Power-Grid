package Billing.api;

/** No wallet exists yet for this zone -- it has never been billed for a tick. */
public class ZoneNotBilledException extends RuntimeException {

    public ZoneNotBilledException(String zoneId) {
        super("No wallet for zone " + zoneId + " -- it hasn't been billed for a tick yet.");
    }
}
