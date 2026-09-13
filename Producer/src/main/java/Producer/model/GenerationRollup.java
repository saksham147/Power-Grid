package Producer.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One plant's generation over one real minute -- which is exactly one simulated
 * hour.
 *
 * <h2>Why a minute</h2>
 *
 * The simulation runs 60x faster than real time. A real minute is 12 ticks, one
 * simulated hour,
 * so a day of rollups still carries 24 points per simulated day and the solar
 * curve survives. A
 * real-hour bucket would span 2.5 simulated days, averaging every day/night cycle
 * into an
 * identical row.
 *
 * <h2>What is exact and what is not</h2>
 *
 * {@code energyMwh} is exact: energy is additive, so the bucket total equals the
 * raw rows it
 * replaced. {@code minOutputMw} and {@code maxOutputMw} keep the extremes. Only the
 * shape
 * <em>within</em> the simulated hour is given up. {@code sampleCount} is normally
 * 12; fewer means
 * a restart or an outage left a gap in that minute.
 *
 * <h2>How rows get here</h2>
 *
 * Never through JPA. Rollups are written by one set-based {@code INSERT ... SELECT}
 * that Postgres
 * executes entirely on its side, which the identity column lets omit the id.
 */
@Entity
@Table(name = "generation_rollup",
        uniqueConstraints = @UniqueConstraint(name = "uq_generation_rollup_plant_bucket",
                columnNames = { "plant_id", "bucket_start" }),
        indexes = @Index(name = "ix_generation_rollup_bucket", columnList = "bucket_start"))
public class GenerationRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private long plantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plant_type", nullable = false)
    private PlantType plantType;

    /** Start of the real minute this row summarises, truncated to the minute. */
    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "avg_output_mw", nullable = false)
    private double avgOutputMw;

    @Column(name = "min_output_mw", nullable = false)
    private double minOutputMw;

    @Column(name = "max_output_mw", nullable = false)
    private double maxOutputMw;

    @Column(name = "energy_mwh", nullable = false)
    private double energyMwh;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /**
     * Lowest and highest tick number in the bucket. Only meaningful as a range when
     * no restart fell
     * inside the minute, since the tick counter resets.
     */
    @Column(name = "first_tick", nullable = false)
    private long firstTick;

    @Column(name = "last_tick", nullable = false)
    private long lastTick;

    protected GenerationRollup() {
        // for JPA; rows are created by SQL, not by this class
    }

    public Long getId() {
        return id;
    }

    public long getPlantId() {
        return plantId;
    }

    public PlantType getPlantType() {
        return plantType;
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public double getAvgOutputMw() {
        return avgOutputMw;
    }

    public double getMinOutputMw() {
        return minOutputMw;
    }

    public double getMaxOutputMw() {
        return maxOutputMw;
    }

    public double getEnergyMwh() {
        return energyMwh;
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
