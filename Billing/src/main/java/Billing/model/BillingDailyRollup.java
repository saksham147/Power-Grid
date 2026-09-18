package Billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One zone's billing history over one simulated day -- the compressed form {@link BillingRecord}
 * ages into once it falls outside the raw retention window. See {@code
 * Billing.history.BillingRollupJob}, and {@code Distributor.model.DistributionDailyRollup} for the
 * sibling table this mirrors (down to the simulated-day-not-wall-clock-time reasoning).
 *
 * <h2>Why totals here, unlike Distributor's avg/min/max</h2>
 *
 * kWh and rupees are additive -- energy and money held over a tick, not an instantaneous reading --
 * so a day's {@code totalKwh}/{@code totalCostRupees} are exact sums of the raw rows they replace,
 * the same reasoning {@code Producer.model.GenerationRollup#energyMwh} already documents.
 * {@code avgRatePerKwh} is kept for reference only; it is not itself used to reconstruct cost.
 *
 * <h2>How rows get here</h2>
 *
 * Never through JPA. Rollups are written by one set-based {@code INSERT ... SELECT} that Postgres
 * executes entirely on its side, which the identity column lets omit the id.
 */
@Entity
@Table(name = "billing_daily_rollup",
        uniqueConstraints = @UniqueConstraint(name = "uq_billing_daily_rollup_zone_day",
                columnNames = { "zone_id", "simulated_day" }),
        indexes = @Index(name = "ix_billing_daily_rollup_day", columnList = "simulated_day"))
public class BillingDailyRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    /** {@code tick_number / 288} -- simulated day 0 is ticks 0-287, day 1 is 288-575, and so on. */
    @Column(name = "simulated_day", nullable = false)
    private long simulatedDay;

    @Column(name = "total_kwh", nullable = false)
    private double totalKwh;

    @Column(name = "total_overage_kwh", nullable = false)
    private double totalOverageKwh;

    @Column(name = "avg_rate_per_kwh", nullable = false)
    private double avgRatePerKwh;

    @Column(name = "total_cost_rupees", nullable = false)
    private double totalCostRupees;

    @Column(name = "total_overage_cost_rupees", nullable = false)
    private double totalOverageCostRupees;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /** Lowest and highest tick number rolled into this day. Only a true range when no restart
     *  reset the tick counter mid-day. */
    @Column(name = "first_tick", nullable = false)
    private long firstTick;

    @Column(name = "last_tick", nullable = false)
    private long lastTick;

    protected BillingDailyRollup() {
        // for JPA; rows are created by SQL, not by this class
    }

    public Long getId() {
        return id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public long getSimulatedDay() {
        return simulatedDay;
    }

    public double getTotalKwh() {
        return totalKwh;
    }

    public double getTotalOverageKwh() {
        return totalOverageKwh;
    }

    public double getAvgRatePerKwh() {
        return avgRatePerKwh;
    }

    public double getTotalCostRupees() {
        return totalCostRupees;
    }

    public double getTotalOverageCostRupees() {
        return totalOverageCostRupees;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public long getFirstTick() {
        return firstTick;
    }

    public long getLastTick() {
        return lastTick;
    }
}
