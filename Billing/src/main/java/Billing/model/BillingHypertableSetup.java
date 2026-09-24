package Billing.model;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Turns {@code billing_record} into a TimescaleDB hypertable and {@code billing_daily_rollup}
 * into a continuous aggregate over it, on whichever startup first finds either isn't done yet --
 * see {@code Grid.model.GridHypertableSetup} and {@code Producer.model.GenerationHypertableSetup}
 * for the siblings this mirrors.
 *
 * <h2>Bucketing: wall-clock day, not simulated day</h2>
 *
 * The old {@code billing_daily_rollup} bucketed by <em>simulated</em> day ({@code tick_number /
 * 288}). TimescaleDB's {@code time_bucket()} only understands wall-clock time, and the two only
 * ever lined up because the tick counter tracked wall-clock time at a fixed 60x ratio -- which a
 * manual clock adjustment (already done once this week, restoring the clock after an outage) can
 * silently break. Bucketing by wall-clock day instead sidesteps that risk entirely: a wall-clock
 * day never depends on the tick counter's own history. Nothing reads a specific day's bucket today
 * (the only consumer, {@link Billing.billing.UnlockService}, only ever wants the all-time total),
 * so this changes nothing anyone currently depends on.
 *
 * <h2>Raw retention: 24 hours, not 15 simulated days</h2>
 *
 * 15 simulated days converts to 15 * 24 real minutes (the same fixed ratio) -- six real hours,
 * an oddly small number to carry forward now that the bucket itself is wall-clock-based. Instead
 * this uses the same 24-hour window {@code GridHypertableSetup}/{@code
 * GenerationHypertableSetup} already use, which is <em>more</em> generous than before, not less --
 * a safe direction to change a retention window in.
 *
 * <h2>Backfill before retention -- the step that actually mattered here</h2>
 *
 * Unlike Grid and Producer, this table's rollup had never once succeeded (a separate, still-open
 * bug -- the old rollup's insert failed a {@code GROUP BY} check every run, so its transaction
 * rolled back and neither the insert nor the paired delete ever committed). The result: {@code
 * billing_daily_rollup} held zero rows, and {@code billing_record} held over a week of raw
 * history no retention had ever pruned. Turning on a 24-hour retention policy against that
 * backlog immediately, before the aggregate had ever captured any of it, would have destroyed most
 * of that week's billing history outright -- including energy sold that counts toward {@link
 * Billing.billing.UnlockService}'s unlock thresholds. So this explicitly backfills the aggregate
 * over the table's <em>entire</em> existing history before the retention policy is added, not
 * only afterward as new data arrives; the old, empty rollup table is simply dropped, since it had
 * nothing worth preserving into a legacy archive the way Producer's did.
 */
@Component
public class BillingHypertableSetup {

    private static final Logger log = LoggerFactory.getLogger(BillingHypertableSetup.class);

    /** See this class's own doc for why 24 hours, not the old rollup's 15 simulated days.
     *  {@code Billing.billing.UnlockService} imports this same constant, rather than a second
     *  copy, to decide which of the two sources a cumulative sum should read from. */
    public static final Duration RAW_RETENTION = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final String schema;

    public BillingHypertableSetup(JdbcTemplate jdbc,
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
            // A failure at any step leaves the affected piece not-yet-migrated, but nothing about
            // the write path or UnlockService's reads depends on any of this having succeeded yet
            // -- it just retries whole on the next restart. Retrying whole matters most for the
            // backfill-before-retention ordering: a partial run must not reach "add the retention
            // policy" without having backfilled first.
            log.error("Could not finish the TimescaleDB setup for {}.billing_record/_rollup; "
                    + "will retry on a later restart", schema, e);
        }
    }

    private void dropLegacyRollupTableIfPresent() {
        Boolean isPlainTable = jdbc.queryForObject(
                "select exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                        + "where n.nspname = ? and c.relname = 'billing_daily_rollup' and c.relkind = 'r')",
                Boolean.class, schema);
        if (!Boolean.TRUE.equals(isPlainTable)) {
            return;
        }
        log.info("Dropping the old, empty {}.billing_daily_rollup table (its own rollup never "
                + "once succeeded, so it never held real data)", schema);
        jdbc.execute("drop table " + schema + ".billing_daily_rollup");
    }

    private void ensureHypertable() {
        String table = schema + ".billing_record";
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.hypertables "
                        + "where hypertable_schema = ? and hypertable_name = 'billing_record')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return;
        }

        log.info("Converting {} into a TimescaleDB hypertable", table);
        jdbc.execute("alter table " + table + " drop constraint if exists billing_record_pkey");
        jdbc.execute("alter table " + table + " drop constraint if exists uq_billing_record_zone_tick");
        jdbc.execute("alter table " + table + " add constraint billing_record_pkey primary key (id, recorded_at)");
        jdbc.execute("alter table " + table
                + " add constraint uq_billing_record_zone_tick unique (zone_id, tick_number, recorded_at)");
        jdbc.execute("select create_hypertable('" + table + "', 'recorded_at', migrate_data => true)");
        log.info("{} is now a hypertable (retention policy added separately, after the backfill)", table);
    }

    /** @return true if the aggregate was just created (and so still needs backfilling), false if
     *          it already existed from a previous startup */
    private boolean ensureContinuousAggregate() {
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.continuous_aggregates "
                        + "where view_schema = ? and view_name = 'billing_daily_rollup')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return false;
        }

        String view = schema + ".billing_daily_rollup";
        String source = schema + ".billing_record";
        log.info("Creating the {} continuous aggregate", view);
        jdbc.execute("""
                create materialized view %s
                with (timescaledb.continuous) as
                select
                  zone_id,
                  max(zone_name) as zone_name,
                  time_bucket('1 day', recorded_at) as bucket_day,
                  sum(kwh) as total_kwh,
                  sum(overage_kwh) as total_overage_kwh,
                  avg(rate_per_kwh) as avg_rate_per_kwh,
                  sum(cost_rupees) as total_cost_rupees,
                  sum(overage_cost_rupees) as total_overage_cost_rupees,
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
        String view = schema + ".billing_daily_rollup";
        log.info("Backfilling {} over the full existing history before retention is enabled", view);
        jdbc.execute("call refresh_continuous_aggregate('" + view + "', null, null)");
    }

    private void ensureRetentionPolicy() {
        String table = schema + ".billing_record";
        String view = schema + ".billing_daily_rollup";

        Boolean retentionExists = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.jobs "
                        + "where hypertable_name = 'billing_record' and proc_name = 'policy_retention')",
                Boolean.class);
        if (!Boolean.TRUE.equals(retentionExists)) {
            jdbc.execute("select add_retention_policy('" + table + "', interval '"
                    + RAW_RETENTION.toHours() + " hours')");
            log.info("{} now has a {}h retention policy", table, RAW_RETENTION.toHours());
        }

        Boolean refreshExists = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.jobs "
                        + "where hypertable_name = 'billing_daily_rollup' "
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
