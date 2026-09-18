package Customer.api;

/**
 * One configured zone, joined against its latest demand.
 *
 * @param zoneId    stable identifier, e.g. {@code "Z-NORTH"}
 * @param name      display name
 * @param unitCount how many houses, factories and commercial buildings currently belong to this
 *                  zone -- a live count, not a historical one, the same way {@code
 *                  Producer.api.dto.SimulationStatusResponse.lastEventCount} is
 * @param demandKw   latest reading; 0 if no tick has run yet, or if this zone was added to
 *                   configuration after the store's last write
 * @param dayPeakKw   this zone's highest demand reading between 06:00 and 18:00 so far today
 *                    (simulated day) -- see {@code Customer.application.PeakTracker}
 * @param nightPeakKw the same, for readings outside that window
 */
public record ZoneDemandResponse(
        String zoneId, String name, long unitCount, double demandKw, double dayPeakKw, double nightPeakKw) {
}
