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

/**
 * One plant's output on one tick. Insert-only: a record of what happened is never
 * edited.
 *
 * <p>
 * Kept for {@link GenerationHypertableSetup#RAW_RETENTION}, then dropped by a TimescaleDB
 * retention policy -- the underlying table is a hypertable, and the older figures live on in
 * {@link GenerationRollupPoint}, a continuous aggregate over it, not a second table a scheduled
 * job copies rows into.
 *
 * <p>
 * The single-column primary key below is Hibernate's starting point on a fresh database, not the
 * final shape: {@link GenerationHypertableSetup} widens it to a composite one including {@code
 * recorded_at} the first time it runs, since {@code create_hypertable} refuses a table whose
 * unique constraints don't include the partitioning column. {@code id} stays the JPA {@code @Id}
 * regardless -- only the database-level constraint shape changes.
 *
 * <h2>Why these rows are not inserted through JPA</h2>
 *
 * The id is {@code IDENTITY}, and Hibernate silently disables JDBC insert batching
 * for identity ids,
 * because it has to read each generated key back one row at a time. This table
 * takes one insert
 * per plant on every tick, so a tick is written by
 * {@code GenerationHistoryWriter} as a single
 * JDBC batch instead. JPA is used here for reading and for bulk deletion only.
 *
 * <p>
 * A pooled {@code SEQUENCE} would have let JPA batch, and was the first design. It
 * broke in
 * practice: Hibernate's {@code ddl-auto: update} checks whether a standalone
 * sequence exists
 * without regard to schema, so a same-named sequence in any other schema of the
 * shared
 * {@code powergrid} database -- the test schema was enough -- made it skip creating
 * this one, and
 * every tick then failed on a missing sequence. An identity column is created as
 * part of the
 * table itself and is immune.
 *
 * <h2>Why there is no foreign key to power_plant</h2>
 *
 * Plants can be deleted. A cascading key would erase a plant's history with it,
 * and a restricting
 * key would forbid deleting any plant that has ever ticked. History is an audit
 * log, so it
 * outlives the plant, carrying the type it needs to stay meaningful on its own.
 */
@Entity
@Table(name = "generation_record", indexes = {
        @Index(name = "ix_generation_record_plant_time", columnList = "plant_id, recorded_at"),
        @Index(name = "ix_generation_record_time", columnList = "recorded_at")
})
public class GenerationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private long plantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plant_type", nullable = false)
    private PlantType plantType;

    /**
     * The simulation tick. Explains solar and wind output, but {@link #recordedAt} is the time
     * axis for queries: a "last 24 hours" window needs a real timestamp, since the tick counter has
     * no fixed relationship to wall-clock time once downtime or a restart is involved. (The counter
     * itself resumes from the highest value recorded here rather than restarting at 0 --
     * {@code SimulationRunner} reads it back on boot -- but that only keeps it monotonic; it does
     * not make it a time axis.)
     */
    @Column(name = "tick_number", nullable = false)
    private long tickNumber;

    @Column(name = "output_mw", nullable = false)
    private double outputMw;

    /** The grid condition the thermal units were responding to on this tick. */
    @Column(name = "frequency_deviation", nullable = false)
    private double frequencyDeviation;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected GenerationRecord() {
        // for JPA
    }

    public GenerationRecord(long plantId, PlantType plantType, long tickNumber, double outputMw,
            double frequencyDeviation, Instant recordedAt) {
        this.plantId = plantId;
        this.plantType = plantType;
        this.tickNumber = tickNumber;
        this.outputMw = outputMw;
        this.frequencyDeviation = frequencyDeviation;
        this.recordedAt = recordedAt;
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

    public long getTickNumber() {
        return tickNumber;
    }

    public double getOutputMw() {
        return outputMw;
    }

    public double getFrequencyDeviation() {
        return frequencyDeviation;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
