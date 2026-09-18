package Distributor.api;

/**
 * Point-in-time view of what Distributor currently knows, for a status page -- not a substitute
 * for {@code distribution_record} or {@code distributor.zone-balance}, which carry the real
 * per-zone history this collapses into one system-wide snapshot.
 *
 * @param totalSupplyKw     fleet-wide generation currently known, in kW
 * @param totalDemandKw     system-wide demand currently known, in kW
 * @param balanceKw         {@code totalSupplyKw - totalDemandKw}
 * @param trackedPlantCount distinct plants that have reported at least once
 * @param trackedZoneCount  distinct zones that have reported at least once
 */
public record DistributionStatusResponse(
        double totalSupplyKw,
        double totalDemandKw,
        double balanceKw,
        int trackedPlantCount,
        int trackedZoneCount) {
}
