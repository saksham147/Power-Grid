package Billing.model;

import java.time.Instant;
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

    /** One zone's billed revenue over a time window -- see {@link #sumRevenueSince}. */
    interface ZoneRevenueRow {
        String getZoneId();

        String getZoneName();

        double getCostRupees();

        double getOverageCostRupees();
    }

    /**
     * What each zone was actually billed since {@code since}, split into total and overage
     * surcharge -- the basis for {@code Billing.billing.MoneyFlowService}'s revenue-per-second
     * figure. Summing real charges over a wall-clock window (rather than reading the latest tick)
     * keeps the rate independent of the tick interval and smooths tick-to-tick demand noise.
     * Only raw rows exist here, which is all a short window ever needs -- rollups are days old.
     */
    @Query("""
            select r.zoneId as zoneId, max(r.zoneName) as zoneName,
                   sum(r.costRupees) as costRupees, sum(r.overageCostRupees) as overageCostRupees
            from BillingRecord r where r.recordedAt >= :since group by r.zoneId""")
    List<ZoneRevenueRow> sumRevenueSince(Instant since);
}
