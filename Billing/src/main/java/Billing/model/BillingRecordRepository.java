package Billing.model;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    /** The idempotency check: has this tick already been billed for this zone? */
    boolean existsByZoneIdAndTickNumber(String zoneId, long tickNumber);

    /** Newest first. */
    @Query("select r from BillingRecord r where r.zoneId = :zoneId order by r.recordedAt desc")
    List<BillingRecord> findByZone(String zoneId, Limit limit);

    /**
     * Bulk delete of everything at or before the cutoff tick. A single statement: no entity is
     * loaded, which matters because a rollup run may cover thousands of rows.
     */
    @Modifying
    @Query("delete from BillingRecord r where r.tickNumber < :cutoffTick")
    int deleteBefore(long cutoffTick);

    /** Highest tick number among raw rows, across every zone, or {@code null} if the table is empty. */
    @Query("select max(r.tickNumber) from BillingRecord r")
    Long findMaxTickNumber();

    /** Every kWh billed across every zone, still raw (not yet rolled up) -- half of {@code
     *  Billing.billing.UnlockService}'s "cumulative energy sold, all time" figure, the other half
     *  being {@link BillingDailyRollupRepository#sumTotalKwh()}. {@code coalesce} so an empty table
     *  reports 0, not null. */
    @Query("select coalesce(sum(r.kwh), 0) from BillingRecord r")
    double sumKwh();
}
