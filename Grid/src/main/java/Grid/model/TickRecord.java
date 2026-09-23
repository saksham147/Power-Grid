package Grid.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One tick's system-wide figures -- frequency deviation, total supply and demand, and whether the
 * grid was overloaded -- kept for the dashboard's history chart. Insert-only, like every other
 * record table in this project: a record of what happened is never edited.
 *
 * <p>
 * Unlike {@code Producer.model.GenerationRecord} (one row per plant per tick) this is one row per
 * tick, full stop -- {@link Grid.simulation.GridClockRunner#tickOnce} already holds the system-wide
 * totals, not a per-plant breakdown, so there is nothing to fan out here.
 *
 * <p>
 * The unique constraint on {@code tick_number} is defensive rather than load-bearing: nothing
 * upstream redelivers a tick the way Kafka redelivers an event, since {@code GridClockRunner} is
 * the sole writer of its own tick counter. It guards against the one scenario that could still
 * double-write it -- a manual replay during testing -- the same way {@link JpaTickHistoryRecorder}
 * checks {@code existsByTickNumber} first rather than relying on the constraint to fail loudly.
 */
@Entity
@Table(name = "tick_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_tick_record_tick_number", columnNames = "tick_number"),
        indexes = @Index(name = "ix_tick_record_time", columnList = "recorded_at"))
public class TickRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tick_number", nullable = false)
    private long tickNumber;

    @Column(name = "simulated_time", nullable = false)
    private String simulatedTime;

    @Column(name = "simulated_day", nullable = false)
    private long simulatedDay;

    @Column(name = "frequency_deviation", nullable = false)
    private double frequencyDeviation;

    @Column(name = "total_supply_kw", nullable = false)
    private double totalSupplyKw;

    @Column(name = "total_demand_kw", nullable = false)
    private double totalDemandKw;

    @Column(name = "load_exceeded", nullable = false)
    private boolean loadExceeded;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TickRecord() {
        // for JPA
    }

    public TickRecord(long tickNumber, String simulatedTime, long simulatedDay, double frequencyDeviation,
            double totalSupplyKw, double totalDemandKw, boolean loadExceeded, Instant recordedAt) {
        this.tickNumber = tickNumber;
        this.simulatedTime = simulatedTime;
        this.simulatedDay = simulatedDay;
        this.frequencyDeviation = frequencyDeviation;
        this.totalSupplyKw = totalSupplyKw;
        this.totalDemandKw = totalDemandKw;
        this.loadExceeded = loadExceeded;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public long getTickNumber() {
        return tickNumber;
    }

    public String getSimulatedTime() {
        return simulatedTime;
    }

    public long getSimulatedDay() {
        return simulatedDay;
    }

    public double getFrequencyDeviation() {
        return frequencyDeviation;
    }

    public double getTotalSupplyKw() {
        return totalSupplyKw;
    }

    public double getTotalDemandKw() {
        return totalDemandKw;
    }

    public boolean isLoadExceeded() {
        return loadExceeded;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
