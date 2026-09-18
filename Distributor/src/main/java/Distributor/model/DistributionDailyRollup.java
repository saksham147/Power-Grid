package Distributor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One zone's balance history over one simulated day -- the compressed form {@link
 * DistributionRecord} ages into once it falls outside the raw retention window. See
 * {@code Distributor.history.DistributionRollupJob}.
 *
 * <h2>Why a simulated day, not a real-time bucket</h2>
 *
 * The retention window this replaces ("last 15 simulation days") is itself defined in simulated
 * days, not wall-clock time -- unlike Producer's minute-real-time rollup, a paused or restarted
 * simulation must not silently shrink or stretch how much history survives. A simulated day is
 * exactly 288 ticks ({@code Grid.simulation.SimulationClock.TICKS_PER_DAY}), so the bucket key is
 * {@code tick_number / 288}, computed in the database alongside the aggregation itself.
 *
 * <h2>What is exact and what is not</h2>
 *
 * Nothing here is summed the way Producer's {@code energyMwh} is -- demand/supplied/balance are
 * instantaneous kW readings, not power held over an interval, so there is no additive total to
 * preserve. avg/min/max keep the shape a day's worth of raw rows had; only the tick-by-tick detail
 * within the day is given up.
 *
 * <h2>How rows get here</h2>
 *
 * Never through JPA. Rollups are written by one set-based {@code INSERT ... SELECT} that Postgres
 * executes entirely on its side, which the identity column lets omit the id -- the same shape
 * {@code Producer.model.GenerationRollup} already documents.
 */
@Entity
@Table(name = "distribution_daily_rollup",
        uniqueConstraints = @UniqueConstraint(name = "uq_distribution_daily_rollup_zone_day",
                columnNames = { "zone_id", "simulated_day" }),
        indexes = @Index(name = "ix_distribution_daily_rollup_day", columnList = "simulated_day"))
public class DistributionDailyRollup {

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

    @Column(name = "avg_demand_kw", nullable = false)
    private double avgDemandKw;

    @Column(name = "min_demand_kw", nullable = false)
    private double minDemandKw;

    @Column(name = "max_demand_kw", nullable = false)
    private double maxDemandKw;

    @Column(name = "avg_supplied_kw", nullable = false)
    private double avgSuppliedKw;

    @Column(name = "min_supplied_kw", nullable = false)
    private double minSuppliedKw;

    @Column(name = "max_supplied_kw", nullable = false)
    private double maxSuppliedKw;

    @Column(name = "avg_balance_kw", nullable = false)
    private double avgBalanceKw;

    @Column(name = "min_balance_kw", nullable = false)
    private double minBalanceKw;

    @Column(name = "max_balance_kw", nullable = false)
    private double maxBalanceKw;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /** Lowest and highest tick number rolled into this day. Only a true range when no restart
     *  reset the tick counter mid-day. */
    @Column(name = "first_tick", nullable = false)
    private long firstTick;

    @Column(name = "last_tick", nullable = false)
    private long lastTick;

    protected DistributionDailyRollup() {
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

    public double getAvgDemandKw() {
        return avgDemandKw;
    }

    public double getMinDemandKw() {
        return minDemandKw;
    }

    public double getMaxDemandKw() {
        return maxDemandKw;
    }

    public double getAvgSuppliedKw() {
        return avgSuppliedKw;
    }

    public double getMinSuppliedKw() {
        return minSuppliedKw;
    }

    public double getMaxSuppliedKw() {
        return maxSuppliedKw;
    }

    public double getAvgBalanceKw() {
        return avgBalanceKw;
    }

    public double getMinBalanceKw() {
        return minBalanceKw;
    }

    public double getMaxBalanceKw() {
        return maxBalanceKw;
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
