package Billing.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Turns {@code wallet_transaction} into a TimescaleDB hypertable and {@code wallet_daily_totals}
 * into a continuous aggregate over it -- see {@code BillingHypertableSetup} for the sibling this
 * mirrors; the shape is the same, but the motivation and the retention story are both different.
 *
 * <h2>Why: a full-table scan, not a data-loss risk</h2>
 *
 * Unlike {@code billing_record}/{@code distribution_record}, this table was never at risk of
 * unbounded raw retention -- {@code wallet_transaction} is the ledger, and nothing in this codebase
 * has ever deleted from it. The problem this solves instead is {@code
 * WalletTransactionRepository#totalsByZoneAndType}'s old query: a plain {@code group by zone_id,
 * type} over the *entire* ledger, run fresh on every {@link Billing.billing.MoneyFlowService}
 * poll -- the frontend's live scoreboard and money-flow panels both read it every few seconds, so
 * the scan cost is paid continuously, and it only grows as the ledger does. Bucketing by day first
 * turns that into a scan over (days elapsed) x (zones) x (transaction types) rows, which stays
 * small for as long as this deployment runs, instead of one row per transaction ever recorded.
 *
 * <h2>No retention policy -- deliberately, unlike every other table this project has hypertabled</h2>
 *
 * {@code add_retention_policy} is never called here. The ledger is never pruned -- {@link
 * WalletTransaction}'s own doc already treats every row as permanent -- so there is no cutoff to
 * add, and {@link #ensureRetentionPolicy} only adds the continuous-aggregate refresh policy that
 * keeps {@code wallet_daily_totals} current.
 *
 * <h2>Staleness: up to the refresh interval, same as every other aggregate here -- and fine for the
 * same reason</h2>
 *
 * This view is only ever as current as its last refresh (every 10 minutes, {@code end_offset} 1
 * hour) -- checked directly against this database: even with {@code timescaledb.materialized_only}
 * turned off, a query against the view did not pick up a transaction written after the last
 * refresh, so this class does not rely on TimescaleDB's real-time aggregation to close that gap
 * (whatever the cause, treating it as unreliable here is the safer assumption). That staleness is
 * invisible in practice for the same reason it already is for {@code billing_daily_rollup}/{@code
 * generation_rollup}/{@code distribution_daily_rollup}: {@link WalletTotalsQuery} only ever feeds
 * {@code Billing.billing.MoneyFlowService}'s <em>lifetime</em> totals (a zone's all-time revenue,
 * fleet spend by category) -- the figures that are meant to move every poll
 * (revenue-per-second, plant running cost) come from {@code BillingRecordRepository} and {@code
 * PlantRosterCache} instead, never from this aggregate.
 */
@Component
public class WalletTransactionHypertableSetup {

    private static final Logger log = LoggerFactory.getLogger(WalletTransactionHypertableSetup.class);

    private final JdbcTemplate jdbc;
    private final String schema;

    public WalletTransactionHypertableSetup(JdbcTemplate jdbc,
            @SuppressWarnings("unused") EntityManagerFactory schemaIsBuilt,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @PostConstruct
    void setUp() {
        try {
            ensureHypertable();
            boolean aggregateIsNew = ensureContinuousAggregate();
            if (aggregateIsNew) {
                backfillEverything();
            }
            ensureRefreshPolicy();
        } catch (Exception e) {
            // A failure at any step leaves the affected piece not-yet-migrated; the raw ledger and
            // WalletTransactionRepository's other queries are untouched either way, so this only
            // costs WalletTotalsQuery its speedup until a later restart retries.
            log.error("Could not finish the TimescaleDB setup for {}.wallet_transaction/_totals; "
                    + "will retry on a later restart", schema, e);
        }
    }

    private void ensureHypertable() {
        String table = schema + ".wallet_transaction";
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.hypertables "
                        + "where hypertable_schema = ? and hypertable_name = 'wallet_transaction')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return;
        }

        log.info("Converting {} into a TimescaleDB hypertable", table);
        jdbc.execute("alter table " + table + " drop constraint if exists wallet_transaction_pkey");
        jdbc.execute("alter table " + table + " add constraint wallet_transaction_pkey primary key (id, occurred_at)");
        jdbc.execute("select create_hypertable('" + table + "', 'occurred_at', migrate_data => true)");
        log.info("{} is now a hypertable (never pruned -- see this class's own doc for why)", table);
    }

    /** @return true if the aggregate was just created (and so still needs backfilling), false if
     *          it already existed from a previous startup */
    private boolean ensureContinuousAggregate() {
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.continuous_aggregates "
                        + "where view_schema = ? and view_name = 'wallet_daily_totals')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return false;
        }

        String view = schema + ".wallet_daily_totals";
        String source = schema + ".wallet_transaction";
        log.info("Creating the {} continuous aggregate", view);
        jdbc.execute("""
                create materialized view %s
                with (timescaledb.continuous) as
                select
                  zone_id,
                  type,
                  time_bucket('1 day', occurred_at) as bucket_day,
                  sum(amount_rupees) as total_rupees,
                  count(*) as sample_count
                from %s
                group by zone_id, type, time_bucket('1 day', occurred_at)
                with no data
                """.formatted(view, source));
        return true;
    }

    private void backfillEverything() {
        String view = schema + ".wallet_daily_totals";
        log.info("Backfilling {} over the full existing ledger", view);
        jdbc.execute("call refresh_continuous_aggregate('" + view + "', null, null)");
    }

    private void ensureRefreshPolicy() {
        String view = schema + ".wallet_daily_totals";

        Boolean refreshExists = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.jobs "
                        + "where hypertable_name = 'wallet_daily_totals' "
                        + "and proc_name = 'policy_refresh_continuous_aggregate')",
                Boolean.class);
        if (!Boolean.TRUE.equals(refreshExists)) {
            jdbc.execute("""
                    select add_continuous_aggregate_policy('%s',
                        start_offset => null,
                        end_offset => interval '1 hour',
                        schedule_interval => interval '10 minutes')
                    """.formatted(view));
            log.info("{} is now refreshed every 10 minutes (stale by up to that interval -- fine, "
                    + "see this class's own doc for why)", view);
        }
    }
}
