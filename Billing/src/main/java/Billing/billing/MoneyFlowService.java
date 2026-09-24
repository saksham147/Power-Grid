package Billing.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import Billing.model.BillingRecordRepository;
import Billing.model.BillingRecordRepository.ZoneRevenueRow;
import Billing.model.TransactionType;
import Billing.model.WalletTotalsQuery;
import Billing.model.WalletZoneTypeTotal;

/**
 * Works out the money-per-second picture the dashboard shows: revenue per zone, the plant fleet's
 * running cost, and lifetime spend by category.
 *
 * <h2>Revenue per second</h2>
 *
 * Summed from what was actually billed in the last {@code billing.flow.window} of wall-clock
 * time, divided by that window. Reading a real window instead of "latest tick's charge over the
 * tick interval" means the figure never depends on a copy of the tick length living in this
 * service, and it doesn't jump with every tick's demand noise. The trade-off is that it ramps up
 * over the first window after Billing starts, and reads lower while ticks are stalled -- both
 * honest, since fewer charges really were made.
 *
 * <h2>Plant running cost per second</h2>
 *
 * {@link MaintenancePricing}'s cost per cycle, spread over the maintenance interval. Maintenance
 * is really charged in one lump per interval; the per-second figure is the steady-state rate that
 * lump amounts to, which is what a per-second display means. An inactive plant, or a disabled
 * maintenance job, owes nothing.
 *
 * <p>
 * Free of Kafka and HTTP; takes {@code now} as an argument so the window is testable without a
 * clock.
 */
@Service
public class MoneyFlowService {

    private final BillingRecordRepository billingRecords;
    private final WalletTotalsQuery totals;
    private final PlantRosterCache roster;
    private final Duration window;
    private final Duration maintenanceInterval;
    private final boolean maintenanceEnabled;

    public MoneyFlowService(BillingRecordRepository billingRecords, WalletTotalsQuery totals,
            PlantRosterCache roster,
            @Value("${billing.flow.window:PT60S}") Duration window,
            @Value("${billing.maintenance.interval:PT10M}") Duration maintenanceInterval,
            @Value("${billing.maintenance.enabled:true}") boolean maintenanceEnabled) {
        this.billingRecords = billingRecords;
        this.totals = totals;
        this.roster = roster;
        this.window = window;
        this.maintenanceInterval = maintenanceInterval;
        this.maintenanceEnabled = maintenanceEnabled;
    }

    public MoneyFlow compute(Instant now) {
        double windowSeconds = seconds(window);

        Map<String, ZoneRevenueRow> recent = new HashMap<>();
        for (ZoneRevenueRow row : billingRecords.sumRevenueSince(now.minus(window))) {
            recent.put(row.getZoneId(), row);
        }

        Map<String, Double> lifetimeRevenueByZone = new HashMap<>();
        Map<TransactionType, Double> totalByType = new EnumMap<>(TransactionType.class);
        for (WalletZoneTypeTotal row : totals.totalsByZoneAndType()) {
            totalByType.merge(row.type(), row.amountRupees(), Double::sum);
            if (row.type() == TransactionType.BILL_DEBIT) {
                lifetimeRevenueByZone.merge(row.zoneId(), row.amountRupees(), Double::sum);
            }
        }

        // Every zone ever billed, plus any with a recent charge -- a zone whose demand has dropped
        // to nothing still belongs on the list, at 0/s, rather than vanishing.
        var zoneIds = new TreeSet<>(lifetimeRevenueByZone.keySet());
        zoneIds.addAll(recent.keySet());

        double revenuePerSecond = 0;
        var zones = new java.util.ArrayList<MoneyFlow.ZoneFlow>();
        for (String zoneId : zoneIds) {
            ZoneRevenueRow row = recent.get(zoneId);
            double perSecond = row == null || windowSeconds <= 0 ? 0 : row.getCostRupees() / windowSeconds;
            double overagePerSecond = row == null || windowSeconds <= 0 ? 0 : row.getOverageCostRupees() / windowSeconds;
            revenuePerSecond += perSecond;
            zones.add(new MoneyFlow.ZoneFlow(zoneId, row == null ? zoneId : row.getZoneName(), perSecond,
                    overagePerSecond, lifetimeRevenueByZone.getOrDefault(zoneId, 0.0)));
        }

        double cycleSeconds = seconds(maintenanceInterval);
        double runningCostPerSecond = 0;
        var plants = new java.util.ArrayList<MoneyFlow.PlantFlow>();
        for (PlantRosterCache.PlantSnapshot plant : roster.allPlants().stream()
                .sorted(Comparator.comparing(PlantRosterCache.PlantSnapshot::plantId)).toList()) {
            double perSecond = plant.active() && maintenanceEnabled && cycleSeconds > 0
                    ? MaintenancePricing.costFor(plant.type(), plant.capacityMw()) / cycleSeconds
                    : 0;
            runningCostPerSecond += perSecond;
            plants.add(new MoneyFlow.PlantFlow(plant.plantId(), plant.type(), plant.capacityMw(), plant.active(),
                    perSecond));
        }

        double purchase = totalByType.getOrDefault(TransactionType.PLANT_PURCHASE, 0.0);
        double upgrade = totalByType.getOrDefault(TransactionType.PLANT_UPGRADE, 0.0);
        double maintenance = totalByType.getOrDefault(TransactionType.PLANT_MAINTENANCE, 0.0);
        double storage = totalByType.getOrDefault(TransactionType.STORAGE_PURCHASE, 0.0);
        double refunds = totalByType.getOrDefault(TransactionType.PLANT_DECOMMISSION, 0.0);
        var spend = new MoneyFlow.SpendTotals(purchase, upgrade, maintenance, storage, refunds,
                purchase + upgrade + maintenance + storage - refunds);

        return new MoneyFlow((long) windowSeconds, revenuePerSecond, runningCostPerSecond,
                revenuePerSecond - runningCostPerSecond, List.copyOf(zones), List.copyOf(plants), spend);
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }
}
