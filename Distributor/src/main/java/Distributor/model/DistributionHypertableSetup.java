package Distributor.model;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Turns {@code distribution_record} into a TimescaleDB hypertable and {@code
 * distribution_daily_rollup} into a continuous aggregate over it -- see {@code
 * Billing.model.BillingHypertableSetup} for the sibling this mirrors almost exactly; only the
 * columns and the absence of any current reader differ.
 *
 * <h2>Bucketing: wall-clock day, not simulated day</h2>
 *
 * Same call as Billing's, for the same reason: the old {@code distribution_daily_rollup} bucketed
 * by <em>simulated</em> day ({@code tick_number / 288}), which only ever lined up with wall-clock
 * time because the tick counter tracked it at a fixed 60x ratio -- a relationship a manual clock
 * adjustment (already done once this week) can silently break. Nothing reads a specific day's
 * bucket today (nothing reads this table at all yet -- see below), so there is no existing
 * consumer whose semantics this could disturb.
 *
 * <h2>Raw retention: 24 hours, not 15 simulated days</h2>
 *
 * Matches Grid/Producer/Billing's own 24-hour window rather than converting "15 simulated days" at
 * the old 60x ratio (which would be an oddly small 6 real hours) -- more generous than before, not
 * less, and keeps all four services on one consistent raw-retention story.
 *
 * <h2>Backfill before retention -- the step that actually mattered here too</h2>
 *
 * This table's rollup had never once succeeded either: live in this database, {@code
 * distribution_daily_rollup} holds zero rows while {@code distribution_record} holds well over a
 * week of raw history (the same still-open {@code GROUP BY} bug {@code BillingHypertableSetup}
 * documents, which failed the old job's insert-then-delete transaction every run). Turning on a
 * 24-hour retention policy against that backlog immediately, before the aggregate had ever captured
 * any of it, would have destroyed most of that history outright. So, exactly like Billing, this
 * explicitly backfills the aggregate over the table's <em>entire</em> existing history before the
 * retention policy is added -- verified with the same {@code InOrder} test shape.
 *
 * <h2>No query class -- unlike Producer/Billing</h2>
 *
 * Nothing in this codebase ever read {@code distribution_daily_rollup} or queried {@code
 * distribution_record} for a range: {@code DistributionController} only exposes {@code
 * GridStateTracker}'s live, in-memory snapshot, and no frontend history view exists for
 * Distributor. So, unlike {@code Producer.history.GenerationHistoryQuery} or {@code
 * Billing.billing.UnlockService}, there is no read path to redesign around the raw/aggregate split
 * -- this component only keeps the same compressed-history safety net the old rollup job provided,
 * available if a future feature needs it.
 */
@Component
public class DistributionHypertableSetup {

    private static final Logger log = LoggerFactory.getLogger(DistributionHypertableSetup.class);

    /** See this class's own doc for why 24 hours, not the old rollup's 15 simulated days. */
    public static final Duration RAW_RETENTION = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final String schema;

    public DistributionHypertableSetup(JdbcTemplate jdbc,
            @SuppressWarnings("unused") EntityManagerFactory schemaIsBuilt,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @PostConstruct
    void setUp() {
        try {
            dropLegacyRollupTableIfPresent();
            ensureHypertable();
            boolean aggregateIsNew = ensureContinuousAggregate();
            if (aggregateIsNew) {
                backfillEverything();
            }
            ensureRetentionPolicy();
        } catch (Exception e) {
            // A failure at any step leaves the affected piece not-yet-migrated, but retries whole
            // on the next restart -- see BillingHypertableSetup for why retrying whole matters
            // most for the backfill-before-retention ordering.
            log.error("Could not finish the TimescaleDB setup for {}.distribution_record/_rollup; "
                    + "will retry on a later restart", schema, e);
        }
    }

    private void dropLegacyRollupTableIfPresent() {
        Boolean isPlainTable = jdbc.queryForObject(
                "select exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                        + "where n.nspname = ? and c.relname = 'distribution_daily_rollup' and c.relkind = 'r')",
                Boolean.class, schema);
        if (!Boolean.TRUE.equals(isPlainTable)) {
            return;
        }
        log.info("Dropping the old, empty {}.distribution_daily_rollup table (its own rollup never "
                + "once succeeded, so it never held real data)", schema);
        jdbc.execute("drop table " + schema + ".distribution_daily_rollup");
    }

    private void ensureHypertable() {
        String table = schema + ".distribution_record";
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.hypertables "
                        + "where hypertable_schema = ? and hypertable_name = 'distribution_record')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return;
        }

        log.info("Converting {} into a TimescaleDB hypertable", table);
        jdbc.execute("alter table " + table + " drop constraint if exists distribution_record_pkey");
        jdbc.execute("alter table " + table + " add constraint distribution_record_pkey primary key (id, recorded_at)");
        jdbc.execute("select create_hypertable('" + table + "', 'recorded_at', migrate_data => true)");
        log.info("{} is now a hypertable (retention policy added separately, after the backfill)", table);
    }

    /** @return true if the aggregate was just created (and so still needs backfilling), false if
     *          it already existed from a previous startup */
    private boolean ensureContinuousAggregate() {
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.continuous_aggregates "
                        + "where view_schema = ? and view_name = 'distribution_daily_rollup')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return false;
        }

        String view = schema + ".distribution_daily_rollup";
        String source = schema + ".distribution_record";
        log.info("Creating the {} continuous aggregate", view);
        jdbc.execute("""
                create materialized view %s
                with (timescaledb.continuous) as
                select
                  zone_id,
                  max(zone_name) as zone_name,
                  time_bucket('1 day', recorded_at) as bucket_day,
                  avg(demand_kw) as avg_demand_kw,
                  min(demand_kw) as min_demand_kw,
                  max(demand_kw) as max_demand_kw,
                  avg(supplied_kw) as avg_supplied_kw,
                  min(supplied_kw) as min_supplied_kw,
                  max(supplied_kw) as max_supplied_kw,
                  avg(balance_kw) as avg_balance_kw,
                  min(balance_kw) as min_balance_kw,
                  max(balance_kw) as max_balance_kw,
                  count(*) as sample_count,
                  min(tick_number) as first_tick,
                  max(tick_number) as last_tick
                from %s
                group by zone_id, time_bucket('1 day', recorded_at)
                with no data
                """.formatted(view, source));
        return true;
    }

    private void backfillEverything() {
        String view = schema + ".distribution_daily_rollup";
        log.info("Backfilling {} over the full existing history before retention is enabled", view);
        jdbc.execute("call refresh_continuous_aggregate('" + view + "', null, null)");
    }

    private void ensureRetentionPolicy() {
        String table = schema + ".distribution_record";
        String view = schema + ".distribution_daily_rollup";

        Boolean retentionExists = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.jobs "
                        + "where hypertable_name = 'distribution_record' and proc_name = 'policy_retention')",
                Boolean.class);
        if (!Boolean.TRUE.equals(retentionExists)) {
            jdbc.execute("select add_retention_policy('" + table + "', interval '"
                    + RAW_RETENTION.toHours() + " hours')");
            log.info("{} now has a {}h retention policy", table, RAW_RETENTION.toHours());
        }

        Boolean refreshExists = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.jobs "
                        + "where hypertable_name = 'distribution_daily_rollup' "
                        + "and proc_name = 'policy_refresh_continuous_aggregate')",
                Boolean.class);
        if (!Boolean.TRUE.equals(refreshExists)) {
            jdbc.execute("""
                    select add_continuous_aggregate_policy('%s',
                        start_offset => null,
                        end_offset => interval '1 hour',
                        schedule_interval => interval '10 minutes')
                    """.formatted(view));
            log.info("{} is now refreshed every 10 minutes", view);
        }
    }
}
