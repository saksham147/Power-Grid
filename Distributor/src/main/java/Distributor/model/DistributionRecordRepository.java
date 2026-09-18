package Distributor.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DistributionRecordRepository extends JpaRepository<DistributionRecord, Long> {

    /**
     * Bulk delete of everything at or before the cutoff tick. A single statement: no entity is
     * loaded, which matters because a rollup run may cover thousands of rows.
     */
    @Modifying
    @Query("delete from DistributionRecord r where r.tickNumber < :cutoffTick")
    int deleteBefore(long cutoffTick);

    /** Highest tick number among raw rows, across every zone, or {@code null} if the table is empty. */
    @Query("select max(r.tickNumber) from DistributionRecord r")
    Long findMaxTickNumber();
}
