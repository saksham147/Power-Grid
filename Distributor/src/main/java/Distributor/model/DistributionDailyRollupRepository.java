package Distributor.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DistributionDailyRollupRepository extends JpaRepository<DistributionDailyRollup, Long> {

    /**
     * Compresses every raw row older than {@code cutoffTick} into one row per zone per simulated
     * day.
     *
     * <p>
     * Native because the aggregation runs entirely inside Postgres: no raw row is ever loaded into
     * the JVM, however large the backlog. {@code {h-schema}} expands to the configured default
     * schema rather than hard-coding {@code distributor}, so the statement follows the schema a
     * test points at.
     *
     * <p>
     * Must run in the same transaction as {@link DistributionRecordRepository#deleteBefore(long)},
     * with the same {@code cutoffTick}, or rows could be summarised and then kept, or deleted
     * without being summarised. The unique key on {@code (zone_id, simulated_day)} is deliberately
     * left to fail the statement on a genuine duplicate rather than silently skip it -- the same
     * reasoning {@code Producer.history.GenerationRollupJob}/{@code GenerationRollup} document for
     * their own insert-then-delete pair: a crash rolls the whole transaction back, and the next run
     * retries from the same, still-raw state.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into {h-schema}distribution_daily_rollup
                (zone_id, zone_name, simulated_day,
                 avg_demand_kw, min_demand_kw, max_demand_kw,
                 avg_supplied_kw, min_supplied_kw, max_supplied_kw,
                 avg_balance_kw, min_balance_kw, max_balance_kw,
                 sample_count, first_tick, last_tick)
            select zone_id,
                   max(zone_name),
                   tick_number / :ticksPerDay,
                   avg(demand_kw), min(demand_kw), max(demand_kw),
                   avg(supplied_kw), min(supplied_kw), max(supplied_kw),
                   avg(balance_kw), min(balance_kw), max(balance_kw),
                   count(*), min(tick_number), max(tick_number)
            from {h-schema}distribution_record
            where tick_number < :cutoffTick
            group by zone_id, tick_number / :ticksPerDay""")
    int rollUpBefore(long cutoffTick, long ticksPerDay);
}
