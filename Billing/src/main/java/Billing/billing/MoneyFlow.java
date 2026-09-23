package Billing.billing;

import java.util.List;

/**
 * A snapshot of where money is moving right now: what consumers are being billed per second, what
 * the plant fleet costs to keep running per second, and lifetime totals behind both. Computed on
 * demand by {@link MoneyFlowService}; nothing here is persisted.
 *
 * @param windowSeconds                   the wall-clock window the per-second revenue figures
 *                                        average over
 * @param revenuePerSecondRupees          every zone's billed revenue, per real second
 * @param plantRunningCostPerSecondRupees the fleet's maintenance cost, per real second
 * @param netPerSecondRupees              revenue minus plant running cost: how fast the Grid
 *                                        Treasury is growing (every bill is credited to it, and
 *                                        upkeep is debited from it)
 */
public record MoneyFlow(
        long windowSeconds,
        double revenuePerSecondRupees,
        double plantRunningCostPerSecondRupees,
        double netPerSecondRupees,
        List<ZoneFlow> zones,
        List<PlantFlow> plants,
        SpendTotals spend) {

    /**
     * @param revenuePerSecondRupees what this zone is being billed, per real second
     * @param overagePerSecondRupees the slice of that charged at the over-capacity surcharge rate
     * @param totalRevenueRupees     everything this zone has ever been billed
     */
    public record ZoneFlow(
            String zoneId,
            String zoneName,
            double revenuePerSecondRupees,
            double overagePerSecondRupees,
            double totalRevenueRupees) {
    }

    /** @param runningCostPerSecondRupees 0 for an inactive plant -- it owes no upkeep */
    public record PlantFlow(
            Long plantId,
            PlantType type,
            double capacityMw,
            boolean active,
            double runningCostPerSecondRupees) {
    }

    /**
     * Lifetime money spent on the fleet, by category. A purchase or upgrade is charged before
     * Producer assigns the plant an id, so it cannot be attributed to one plant -- these are
     * fleet-wide totals, and per-plant figures are limited to running cost.
     *
     * @param netRupees everything spent minus what decommission refunds paid back
     */
    public record SpendTotals(
            double purchaseRupees,
            double upgradeRupees,
            double maintenanceRupees,
            double storageRupees,
            double decommissionRefundRupees,
            double netRupees) {
    }
}
