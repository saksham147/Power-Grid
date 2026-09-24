package Grid.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Turns {@code tick_record} into a TimescaleDB hypertable with a 24h retention policy, on
 * whichever startup first finds it isn't one yet -- so this migration is a property of the
 * application, not a one-off manual step a human has to remember to run against a fresh install.
 *
 * <p>
 * Two things Hibernate's {@code ddl-auto: update} cannot do on its own, which is why this exists
 * at all (the same reason {@code Billing.model.TransactionTypeConstraintSync} exists -- a schema
 * change {@code update} mode has no vocabulary for):
 *
 * <ol>
 * <li>{@code create_hypertable} refuses a table whose primary key or unique constraints don't
 * include the partitioning column ({@code recorded_at}). Hibernate only ever generates {@code
 * @Id}'s single-column primary key and the entity's own single-column {@code @UniqueConstraint}
 * on {@code tick_number} -- neither includes it, so both have to be dropped and re-created as
 * composite constraints first. {@code id} stays the JPA {@code @Id}; {@code
 * GenerationType.IDENTITY} already guarantees it unique on its own regardless of what the
 * database-level constraint shape is.</li>
 * <li>{@code create_hypertable}/{@code add_retention_policy} are TimescaleDB functions, not DDL
 * Hibernate has any concept of emitting in the first place.</li>
 * </ol>
 *
 * <p>
 * Runs after Hibernate has built the schema (depends on {@link EntityManagerFactory}, whose
 * creation is what runs the DDL), and is idempotent: {@code timescaledb_information.hypertables}
 * is checked first, so a restart that finds the table already converted does nothing.
 */
@Component
public class GridHypertableSetup {

    private static final Logger log = LoggerFactory.getLogger(GridHypertableSetup.class);

    private final JdbcTemplate jdbc;
    private final String schema;

    public GridHypertableSetup(JdbcTemplate jdbc,
            @SuppressWarnings("unused") EntityManagerFactory schemaIsBuilt,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @PostConstruct
    void ensureHypertable() {
        String table = schema + ".tick_record";
        try {
            Boolean already = jdbc.queryForObject(
                    "select exists (select 1 from timescaledb_information.hypertables "
                            + "where hypertable_schema = ? and hypertable_name = 'tick_record')",
                    Boolean.class, schema);
            if (Boolean.TRUE.equals(already)) {
                return;
            }

            log.info("Converting {} into a TimescaleDB hypertable", table);
            jdbc.execute("alter table " + table + " drop constraint if exists tick_record_pkey");
            jdbc.execute("alter table " + table + " drop constraint if exists uq_tick_record_tick_number");
            jdbc.execute("alter table " + table + " add constraint tick_record_pkey primary key (id, recorded_at)");
            jdbc.execute("alter table " + table
                    + " add constraint uq_tick_record_tick_number unique (tick_number, recorded_at)");
            jdbc.execute("select create_hypertable('" + table + "', 'recorded_at', migrate_data => true)");
            jdbc.execute("select add_retention_policy('" + table + "', interval '24 hours')");
            log.info("{} is now a hypertable with a 24h retention policy", table);
        } catch (Exception e) {
            // A failure here leaves tick_record as a plain table -- history still reads and
            // writes correctly (nothing about the JPA layer depends on it being a hypertable),
            // it just never gets pruned automatically until this succeeds on a later restart.
            log.error("Could not convert {} into a hypertable; it will keep growing unpruned "
                    + "until this succeeds on a later restart", table, e);
        }
    }
}
