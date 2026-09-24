package Producer.model;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Turns {@code generation_record} into a TimescaleDB hypertable and {@code generation_rollup}
 * into a continuous aggregate over it, on whichever startup first finds either isn't done yet --
 * see {@code Grid.model.GridHypertableSetup} for the sibling this mirrors, and {@code
 * Billing.model.TransactionTypeConstraintSync} for the pattern both follow: a schema change
 * Hibernate's {@code ddl-auto: update} has no vocabulary for, codified as application startup
 * behaviour instead of a one-off manual step.
 *
 * <p>
 * Four independent, individually-idempotent steps, run in order:
 *
 * <ol>
 * <li><b>Archive the old rollup table.</b> {@code generation_rollup} used to be a plain table,
 * written by a scheduled job. That job is gone; the table is renamed to {@code
 * generation_rollup_legacy} (once -- if it's already been renamed, or this is a fresh install
 * that never had it, there is nothing to do) rather than dropped, since it holds real history
 * with no raw rows left behind it to recompute from.</li>
 * <li><b>Hypertable {@code generation_record}.</b> Same constraint problem {@code
 * GridHypertableSetup} solves for {@code tick_record}: {@code create_hypertable} refuses a table
 * whose primary key doesn't include the partitioning column, and Hibernate only ever generates
 * {@code @Id}'s single-column one.</li>
 * <li><b>Create the continuous aggregate.</b> {@code generation_rollup} is now a materialized
 * view TimescaleDB keeps updated, grouped per real minute -- the same granularity and the same
 * {@code (sum(output_mw) * 5) / 60} energy formula the old {@code GenerationRollupJob} used, so a
 * bucket means exactly what it always meant.</li>
 * <li><b>Add both policies.</b> A refresh policy for the aggregate (matches the old rollup
 * interval) and a retention policy for the raw hypertable (matches {@code
 * producer.history.raw-retention}) -- two independent TimescaleDB background jobs replacing the
 * one Spring {@code @Scheduled} job this used to be.</li>
 * </ol>
 */
@Component
public class GenerationHypertableSetup {

    private static final Logger log = LoggerFactory.getLogger(GenerationHypertableSetup.class);

    /** How long raw rows survive before TimescaleDB's own retention policy drops them -- set here
     *  in SQL below, not read from application config, the same choice {@code
     *  Grid.model.GridHypertableSetup} makes: once retention is a database policy, the database's
     *  own {@code timescaledb_information.jobs} is the single source of truth for it, not a
     *  Spring property that could drift from what the policy actually enforces. {@code
     *  Producer.history.GenerationHistoryQuery} imports this same constant, rather than a second
     *  copy, to decide which of the two tables a given time range should read from. */
    public static final Duration RAW_RETENTION = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final String schema;

    public GenerationHypertableSetup(JdbcTemplate jdbc,
            @SuppressWarnings("unused") EntityManagerFactory schemaIsBuilt,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @PostConstruct
    void setUp() {
        try {
            archiveLegacyRollupTableIfPresent();
            ensureHypertable();
            ensureContinuousAggregate();
        } catch (Exception e) {
            // A failure at any step leaves the affected piece not-yet-migrated -- generation_record
            // stays a plain table, or generation_rollup stays absent/un-refreshed -- but nothing
            // about the JPA write path or GenerationRollupQuery's reads depends on any of this
            // having succeeded yet; it just retries whole on the next restart.
            log.error("Could not finish the TimescaleDB setup for {}.generation_record/_rollup; "
                    + "will retry on a later restart", schema, e);
        }
    }

    private void archiveLegacyRollupTableIfPresent() {
        Boolean isPlainTable = jdbc.queryForObject(
                "select exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                        + "where n.nspname = ? and c.relname = 'generation_rollup' and c.relkind = 'r')",
                Boolean.class, schema);
        if (!Boolean.TRUE.equals(isPlainTable)) {
            return;
        }
        log.info("Archiving the old {}.generation_rollup table as generation_rollup_legacy", schema);
        jdbc.execute("alter table " + schema + ".generation_rollup rename to generation_rollup_legacy");
    }

    private void ensureHypertable() {
        String table = schema + ".generation_record";
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.hypertables "
                        + "where hypertable_schema = ? and hypertable_name = 'generation_record')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return;
        }

        log.info("Converting {} into a TimescaleDB hypertable", table);
        jdbc.execute("alter table " + table + " drop constraint if exists generation_record_pkey");
        jdbc.execute("alter table " + table + " add constraint generation_record_pkey primary key (id, recorded_at)");
        jdbc.execute("select create_hypertable('" + table + "', 'recorded_at', migrate_data => true)");
        jdbc.execute("select add_retention_policy('" + table + "', interval '" + RAW_RETENTION.toHours() + " hours')");
        log.info("{} is now a hypertable with a 24h retention policy", table);
    }

    private void ensureContinuousAggregate() {
        Boolean already = jdbc.queryForObject(
                "select exists (select 1 from timescaledb_information.continuous_aggregates "
                        + "where view_schema = ? and view_name = 'generation_rollup')",
                Boolean.class, schema);
        if (Boolean.TRUE.equals(already)) {
            return;
        }

        String view = schema + ".generation_rollup";
        String source = schema + ".generation_record";
        log.info("Creating the {} continuous aggregate", view);
        // Same grouping (per real minute) and the same energy formula (sum(output_mw) * 5 / 60,
        // 5 being SimulationClock.SIMULATED_MINUTES_PER_TICK) the old GenerationRollupJob used --
        // a bucket has to mean what it always meant, not a different number under the same name.
        jdbc.execute("""
                create materialized view %s
                with (timescaledb.continuous) as
                select
                  plant_id,
                  plant_type,
                  time_bucket('1 minute', recorded_at) as bucket_start,
                  avg(output_mw) as avg_output_mw,
                  min(output_mw) as min_output_mw,
                  max(output_mw) as max_output_mw,
                  (sum(output_mw) * 5.0) / 60.0 as energy_mwh,
                  count(*) as sample_count,
                  min(tick_number) as first_tick,
                  max(tick_number) as last_tick
                from %s
                group by plant_id, plant_type, time_bucket('1 minute', recorded_at)
                with no data
                """.formatted(view, source));
        jdbc.execute("""
                select add_continuous_aggregate_policy('%s',
                    start_offset => null,
                    end_offset => interval '1 minute',
                    schedule_interval => interval '10 minutes')
                """.formatted(view));
        log.info("{} is now a continuous aggregate, refreshed every 10 minutes", view);
    }
}
