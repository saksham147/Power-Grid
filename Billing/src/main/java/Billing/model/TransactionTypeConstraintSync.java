package Billing.model;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Keeps the database's CHECK constraint on {@code wallet_transaction.type} in step with {@link
 * TransactionType}.
 *
 * <p>
 * This project builds its schema with {@code ddl-auto: update}, which creates a constraint listing
 * an enum's values the first time the table is made and then never touches it again. Add a value
 * to the enum -- as {@code BILL_REVENUE} was -- and a database that already exists keeps rejecting
 * it: every insert of the new type fails the CHECK, and here that means every billing cycle fails.
 * A fresh database is fine; an existing one is not, and nothing warns you until billing stops.
 *
 * <p>
 * So on startup this compares the live constraint against the enum and, only if a value is
 * missing, drops and re-creates it with the full list. It runs after Hibernate has built the schema
 * (it depends on the {@link EntityManagerFactory}, whose creation is what runs the DDL) and before
 * any Kafka listener starts, so no bill can hit the old constraint. A failure is logged, not
 * thrown -- a context that cannot reach a database (a unit-test run) must still start.
 */
@Component
public class TransactionTypeConstraintSync {

    static final String CONSTRAINT = "wallet_transaction_type_check";

    private static final Logger log = LoggerFactory.getLogger(TransactionTypeConstraintSync.class);

    private final JdbcTemplate jdbc;
    private final String schema;

    public TransactionTypeConstraintSync(JdbcTemplate jdbc,
            @SuppressWarnings("unused") EntityManagerFactory schemaIsBuilt,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @PostConstruct
    void sync() {
        try {
            String table = schema + ".wallet_transaction";
            List<String> definitions = jdbc.queryForList(
                    "select pg_get_constraintdef(oid) from pg_constraint where conname = ? and conrelid = to_regclass(?)",
                    String.class, CONSTRAINT, table);
            if (definitions.isEmpty()) {
                return; // no such constraint, so nothing is restricting the values
            }
            List<String> missing = missingValues(definitions.get(0));
            if (missing.isEmpty()) {
                return;
            }
            log.warn("{} does not allow {}; re-creating it from the enum", CONSTRAINT, missing);
            jdbc.execute("alter table " + table + " drop constraint " + CONSTRAINT);
            jdbc.execute("alter table " + table + " add constraint " + CONSTRAINT + " " + checkClause());
        } catch (Exception e) {
            log.warn("Could not verify the {} constraint: {}", CONSTRAINT, e.toString());
        }
    }

    /** The enum values a constraint definition does not yet list. */
    static List<String> missingValues(String constraintDefinition) {
        return Arrays.stream(TransactionType.values())
                .map(Enum::name)
                .filter(name -> !constraintDefinition.contains("'" + name + "'"))
                .toList();
    }

    /** {@code check (type in ('A', 'B', ...))} over every current enum value. */
    static String checkClause() {
        String values = String.join(", ", Arrays.stream(TransactionType.values())
                .map(t -> "'" + t.name() + "'").toList());
        return "check (type in (" + values + "))";
    }
}
