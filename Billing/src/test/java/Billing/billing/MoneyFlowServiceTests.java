package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import Billing.model.BillingRecordRepository;
import Billing.model.BillingRecordRepository.ZoneRevenueRow;
import Billing.model.TransactionType;
import Billing.model.WalletTotalsQuery;
import Billing.model.WalletZoneTypeTotal;

/**
 * The per-second arithmetic and the lifetime totals: revenue is a real billed window divided by
 * the window, plant running cost is a maintenance cycle spread over its interval, and an inactive
 * plant or a disabled job owes nothing.
 */
class MoneyFlowServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final BillingRecordRepository billingRecords = mock(BillingRecordRepository.class);
    private final WalletTotalsQuery totals = mock(WalletTotalsQuery.class);
    private final PlantRosterCache roster = mock(PlantRosterCache.class);

    private MoneyFlowService service(boolean maintenanceEnabled) {
        return new MoneyFlowService(billingRecords, totals, roster, Duration.ofSeconds(60),
                Duration.ofMinutes(10), maintenanceEnabled);
    }

    // A plain implementation of the projection interface, not a mock: building a mock inside
    // another mock's `given(...)` call trips Mockito's "unfinished stubbing" check.
    private record RevenueRow(String zoneId, String zoneName, double cost, double overage) implements ZoneRevenueRow {
        @Override
        public String getZoneId() {
            return zoneId;
        }

        @Override
        public String getZoneName() {
            return zoneName;
        }

        @Override
        public double getCostRupees() {
            return cost;
        }

        @Override
        public double getOverageCostRupees() {
            return overage;
        }
    }

    private static ZoneRevenueRow revenue(String zoneId, String name, double cost, double overage) {
        return new RevenueRow(zoneId, name, cost, overage);
    }

    // WalletZoneTypeTotal is a plain record now (WalletTotalsQuery reads it off a continuous
    // aggregate view via JdbcTemplate, not a Spring Data projection), so tests construct it
    // directly -- no implementing wrapper record needed the way TotalRow used to provide one.
    private static WalletZoneTypeTotal total(String zoneId, TransactionType type, double amount) {
        return new WalletZoneTypeTotal(zoneId, type, amount);
    }

    @Test
    void revenuePerSecondIsTheBilledWindowDividedByTheWindow() {
        // 6000 billed over a 60s window = 100/s for Z-A; 3000 = 50/s for Z-B, 600 of it surcharge.
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of(
                revenue("Z-A", "North", 6000.0, 0.0),
                revenue("Z-B", "East", 3000.0, 600.0)));
        given(totals.totalsByZoneAndType()).willReturn(List.of(
                total("Z-A", TransactionType.BILL_DEBIT, 90000.0),
                total("Z-B", TransactionType.BILL_DEBIT, 45000.0)));
        given(roster.allPlants()).willReturn(List.of());

        MoneyFlow flow = service(true).compute(NOW);

        assertThat(flow.revenuePerSecondRupees()).isCloseTo(150.0, within(1e-9));
        assertThat(flow.zones()).hasSize(2);
        assertThat(flow.zones().get(0).zoneId()).isEqualTo("Z-A");
        assertThat(flow.zones().get(0).revenuePerSecondRupees()).isCloseTo(100.0, within(1e-9));
        assertThat(flow.zones().get(0).totalRevenueRupees()).isEqualTo(90000.0);
        assertThat(flow.zones().get(1).overagePerSecondRupees()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void theWindowIsMeasuredBackFromNow() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of());
        given(roster.allPlants()).willReturn(List.of());

        service(true).compute(NOW);

        var since = ArgumentCaptor.forClass(Instant.class);
        verify(billingRecords).sumRevenueSince(since.capture());
        assertThat(since.getValue()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void aZoneWithNoRecentChargeStaysListedAtZeroPerSecond() {
        // Billed in the past (lifetime total), nothing in the last window: demand fell to zero.
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of(
                total("Z-A", TransactionType.BILL_DEBIT, 5000.0)));
        given(roster.allPlants()).willReturn(List.of());

        MoneyFlow flow = service(true).compute(NOW);

        assertThat(flow.revenuePerSecondRupees()).isZero();
        assertThat(flow.zones()).singleElement().satisfies(z -> {
            assertThat(z.zoneId()).isEqualTo("Z-A");
            assertThat(z.revenuePerSecondRupees()).isZero();
            assertThat(z.totalRevenueRupees()).isEqualTo(5000.0);
        });
    }

    @Test
    void plantRunningCostIsTheMaintenanceCycleSpreadOverItsInterval() {
        // Thermal 900MW @ 30/MW = 27000 per 600s cycle = 45/s; Wind 150MW @ 15/MW = 2250 -> 3.75/s.
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of());
        given(roster.allPlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(2L, PlantType.WIND, 150.0, true),
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 900.0, true)));

        MoneyFlow flow = service(true).compute(NOW);

        assertThat(flow.plantRunningCostPerSecondRupees()).isCloseTo(48.75, within(1e-9));
        assertThat(flow.plants()).extracting(MoneyFlow.PlantFlow::plantId).containsExactly(1L, 2L);
        assertThat(flow.plants().get(0).runningCostPerSecondRupees()).isCloseTo(45.0, within(1e-9));
        assertThat(flow.plants().get(1).runningCostPerSecondRupees()).isCloseTo(3.75, within(1e-9));
    }

    @Test
    void anInactivePlantIsListedButOwesNoUpkeep() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of());
        given(roster.allPlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 900.0, false)));

        MoneyFlow flow = service(true).compute(NOW);

        assertThat(flow.plantRunningCostPerSecondRupees()).isZero();
        assertThat(flow.plants()).singleElement().satisfies(p -> {
            assertThat(p.active()).isFalse();
            assertThat(p.runningCostPerSecondRupees()).isZero();
        });
    }

    @Test
    void aDisabledMaintenanceJobMeansNoRunningCost() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of());
        given(roster.allPlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 900.0, true)));

        assertThat(service(false).compute(NOW).plantRunningCostPerSecondRupees()).isZero();
    }

    @Test
    void netIsRevenueMinusPlantRunningCost() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of(revenue("Z-A", "North", 600.0, 0.0)));
        given(totals.totalsByZoneAndType()).willReturn(List.of(
                total("Z-A", TransactionType.BILL_DEBIT, 600.0)));
        given(roster.allPlants()).willReturn(List.of(
                new PlantRosterCache.PlantSnapshot(1L, PlantType.THERMAL, 100.0, true)));

        MoneyFlow flow = service(true).compute(NOW);

        // Revenue 600/60 = 10/s; upkeep 100MW @ 30/MW = 3000 per 600s cycle = 5/s.
        assertThat(flow.netPerSecondRupees()).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void lifetimeSpendIsGroupedByCategoryAndRefundsComeOffTheNet() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of(
                total("GRID", TransactionType.PLANT_PURCHASE, 10000.0),
                total("GRID", TransactionType.PLANT_UPGRADE, 2000.0),
                total("GRID", TransactionType.PLANT_MAINTENANCE, 3000.0),
                total("GRID", TransactionType.STORAGE_PURCHASE, 1500.0),
                total("GRID", TransactionType.PLANT_DECOMMISSION, 4000.0),
                total("Z-A", TransactionType.BILL_DEBIT, 999999.0)));
        given(roster.allPlants()).willReturn(List.of());

        MoneyFlow.SpendTotals spend = service(true).compute(NOW).spend();

        assertThat(spend.purchaseRupees()).isEqualTo(10000.0);
        assertThat(spend.upgradeRupees()).isEqualTo(2000.0);
        assertThat(spend.maintenanceRupees()).isEqualTo(3000.0);
        assertThat(spend.storageRupees()).isEqualTo(1500.0);
        assertThat(spend.decommissionRefundRupees()).isEqualTo(4000.0);
        // 10000 + 2000 + 3000 + 1500 - 4000; a customer's bill is revenue, never plant spend.
        assertThat(spend.netRupees()).isEqualTo(12500.0);
    }

    @Test
    void anEmptyLedgerYieldsAllZeros() {
        given(billingRecords.sumRevenueSince(any())).willReturn(List.of());
        given(totals.totalsByZoneAndType()).willReturn(List.of());
        given(roster.allPlants()).willReturn(List.of());

        MoneyFlow flow = service(true).compute(NOW);

        assertThat(flow.revenuePerSecondRupees()).isZero();
        assertThat(flow.plantRunningCostPerSecondRupees()).isZero();
        assertThat(flow.netPerSecondRupees()).isZero();
        assertThat(flow.zones()).isEmpty();
        assertThat(flow.plants()).isEmpty();
        assertThat(flow.spend().netRupees()).isZero();
    }
}
