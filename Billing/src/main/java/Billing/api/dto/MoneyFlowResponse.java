package Billing.api.dto;

import java.util.List;

import Billing.billing.MoneyFlow;
import Billing.billing.PlantType;

/**
 * Where money is moving right now -- see {@link MoneyFlow} for what each figure means and how it
 * is computed. A straight field-for-field copy: this exists so the wire format is its own type,
 * free to change without touching the billing package, the same split every other response here
 * makes.
 */
public record MoneyFlowResponse(
        long windowSeconds,
        double revenuePerSecondRupees,
        double plantRunningCostPerSecondRupees,
        double netPerSecondRupees,
        List<ZoneFlow> zones,
        List<PlantFlow> plants,
        SpendTotals spend) {

    public record ZoneFlow(
            String zoneId,
            String zoneName,
            double revenuePerSecondRupees,
            double overagePerSecondRupees,
            double totalRevenueRupees) {
    }

    public record PlantFlow(
            Long plantId,
            PlantType type,
            double capacityMw,
            boolean active,
            double runningCostPerSecondRupees) {
    }

    public record SpendTotals(
            double purchaseRupees,
            double upgradeRupees,
            double maintenanceRupees,
            double storageRupees,
            double decommissionRefundRupees,
            double netRupees) {
    }

    public static MoneyFlowResponse from(MoneyFlow flow) {
        return new MoneyFlowResponse(
                flow.windowSeconds(),
                flow.revenuePerSecondRupees(),
                flow.plantRunningCostPerSecondRupees(),
                flow.netPerSecondRupees(),
                flow.zones().stream()
                        .map(z -> new ZoneFlow(z.zoneId(), z.zoneName(), z.revenuePerSecondRupees(),
                                z.overagePerSecondRupees(), z.totalRevenueRupees()))
                        .toList(),
                flow.plants().stream()
                        .map(p -> new PlantFlow(p.plantId(), p.type(), p.capacityMw(), p.active(),
                                p.runningCostPerSecondRupees()))
                        .toList(),
                new SpendTotals(flow.spend().purchaseRupees(), flow.spend().upgradeRupees(),
                        flow.spend().maintenanceRupees(), flow.spend().storageRupees(),
                        flow.spend().decommissionRefundRupees(), flow.spend().netRupees()));
    }
}
