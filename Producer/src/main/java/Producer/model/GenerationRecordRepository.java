package Producer.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GenerationRecordRepository extends JpaRepository<GenerationRecord, Long> {

    /**
     * Bulk delete of everything recorded before the cutoff. A single statement: no
     * entity is loaded,
     * which matters because a rollup run may cover thousands of rows.
     */
    @Modifying
    @Query("delete from GenerationRecord r where r.recordedAt < :cutoff")
    int deleteRecordedBefore(Instant cutoff);

    /** Newest first, half-open {@code [from, to)} so adjacent ranges never share a row. */
    @Query("""
            select r from GenerationRecord r
            where r.plantId = :plantId and r.recordedAt >= :from and r.recordedAt < :to
            order by r.recordedAt desc""")
    List<GenerationRecord> findHistory(long plantId, Instant from, Instant to, Limit limit);

    /** Highest tick number among raw rows, across every plant, or {@code null} if the table is empty. */
    @Query("select max(r.tickNumber) from GenerationRecord r")
    Long findMaxTickNumber();
}
