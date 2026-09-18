package Billing.billing;

/**
 * A purchase was attempted for a plant type not yet unlocked. Mapped to 403 by {@code
 * Billing.api.ApiExceptionHandler} -- the request is well-formed, it is simply not yet permitted,
 * the same distinction 402 draws for insufficient funds.
 */
public class PlantTypeLockedException extends RuntimeException {

    public PlantTypeLockedException(PlantType type, double thresholdKwh, double cumulativeKwhSold) {
        super("%s is locked until %.0f kWh has been sold grid-wide (currently %.0f)"
                .formatted(type, thresholdKwh, cumulativeKwhSold));
    }
}
