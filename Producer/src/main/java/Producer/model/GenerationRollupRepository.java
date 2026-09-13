package Producer.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GenerationRollupRepository extends JpaRepository<GenerationRollup, Long> {

    /**
     * Compresses every raw row recorded before the cutoff into one row per plant per
     * real minute.
     *
     * <p>
     * Native because the aggregation runs entirely inside Postgres: no raw row is
     * ever loaded into
     * the JVM, however large the backlog. {@code {h-schema}} expands to the configured
     * default schema
     * rather than hard-coding {@code producer}, so the statement follows the schema a
     * test points at.
     *
     * <p>
     * Energy is summed per tick -- output held for the tick's simulated minutes -- so
     * a bucket's
     * {@code energy_mwh} is exactly the total of the raw rows it replaces.
     *
     * <p>
     * Must run in the same transaction as
     * {@link GenerationRecordRepository#deleteRecordedBefore},
     * with the same cutoff, or rows could be summarised and then kept, or deleted
     * without being
     * summarised.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into {h-schema}generation_rollup
                (plant_id, plant_type, bucket_start,
                 avg_output_mw, min_output_mw, max_output_mw,
                 energy_mwh, sample_count, first_tick, last_tick)
            select plant_id,
                   plant_type,
                   date_trunc('minute', recorded_at),
                   avg(output_mw),
                   min(output_mw),
                   max(output_mw),
                   (sum(output_mw) * :minutesPerTick) / 60,
                   count(*),
                   min(tick_number),
                   max(tick_number)
            from {h-schema}generation_record
            where recorded_at < :cutoff
            group by plant_id, plant_type, date_trunc('minute', recorded_at)""")
    int rollUpRecordedBefore(Instant cutoff, long minutesPerTick);

    /** Newest first, half-open {@code [from, to)}. */
    @Query("""
            select r from GenerationRollup r
            where r.plantId = :plantId and r.bucketStart >= :from and r.bucketStart < :to
            order by r.bucketStart desc""")
    List<GenerationRollup> findHistory(long plantId, Instant from, Instant to, Limit limit);

    /** Highest {@code last_tick} among rollup rows, across every plant, or {@code null} if the table is empty. */
    @Query("select max(r.lastTick) from GenerationRollup r")
    Long findMaxLastTick();
}
