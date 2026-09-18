package Billing.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BillingDailyRollupRepository extends JpaRepository<BillingDailyRollup, Long> {

    /** The rolled-up half of "cumulative energy sold, all time" -- see {@link
     *  BillingRecordRepository#sumKwh()} for the raw half; a rollup replaces the raw rows it
     *  summarises, so summing both, never double-counts either side. */
    @Query("select coalesce(sum(r.totalKwh), 0) from BillingDailyRollup r")
    double sumTotalKwh();

    /**
     * Compresses every raw row older than {@code cutoffTick} into one row per zone per simulated
     * day.
     *
     * <p>
     * Native because the aggregation runs entirely inside Postgres: no raw row is ever loaded into
     * the JVM, however large the backlog. {@code {h-schema}} expands to the configured default
     * schema rather than hard-coding {@code billing}, so the statement follows the schema a test
     * points at.
     *
     * <p>
     * Must run in the same transaction as {@link BillingRecordRepository#deleteBefore(long)}, with
     * the same {@code cutoffTick}, or rows could be summarised and then kept, or deleted without
     * being summarised. The unique key on {@code (zone_id, simulated_day)} is deliberately left to
     * fail the statement on a genuine duplicate rather than silently skip it -- the same reasoning
     * {@code Distributor.model.DistributionDailyRollupRepository}/{@code
     * Producer.history.GenerationRollupJob} document for their own insert-then-delete pairs.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into {h-schema}billing_daily_rollup
                (zone_id, zone_name, simulated_day,
                 total_kwh, total_overage_kwh, avg_rate_per_kwh,
                 total_cost_rupees, total_overage_cost_rupees,
                 sample_count, first_tick, last_tick)
            select zone_id,
                   max(zone_name),
                   tick_number / :ticksPerDay,
                   sum(kwh), sum(overage_kwh), avg(rate_per_kwh),
                   sum(cost_rupees), sum(overage_cost_rupees),
                   count(*), min(tick_number), max(tick_number)
            from {h-schema}billing_record
            where tick_number < :cutoffTick
            group by zone_id, tick_number / :ticksPerDay""")
    int rollUpBefore(long cutoffTick, long ticksPerDay);
}
