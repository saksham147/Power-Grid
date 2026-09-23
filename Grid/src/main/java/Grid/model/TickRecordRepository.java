package Grid.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TickRecordRepository extends JpaRepository<TickRecord, Long> {

    /** The idempotency check {@link JpaTickHistoryRecorder} uses before inserting. */
    boolean existsByTickNumber(long tickNumber);

    /** Newest first -- the dashboard's history chart reads it oldest-first itself, since that's the
     *  order a line chart draws in, but "newest N" is what a caller actually wants to ask for. */
    @Query("select r from TickRecord r order by r.tickNumber desc")
    List<TickRecord> findRecent(Limit limit);

    /** Bulk delete of everything recorded before the cutoff -- see Producer's own {@code
     *  GenerationRecordRepository#deleteRecordedBefore} for the same wall-clock-retention shape.
     *  A single statement: no entity is loaded, which matters if a prune run covers many rows. */
    @Modifying
    @Query("delete from TickRecord r where r.recordedAt < :cutoff")
    int deleteRecordedBefore(Instant cutoff);
}
