package Producer.history;

import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import Producer.model.GenerationRecord;

/**
 * Writes a tick's history as one JDBC batch.
 *
 * <p>
 * {@code generation_record} uses an identity id, and Hibernate will not batch
 * inserts for identity
 * ids -- so going through JPA would cost a round trip per plant per tick. A
 * {@link JdbcTemplate}
 * batch has no such limit: the identity column fills itself in and nothing needs
 * reading back.
 *
 * <h2>It still shares the tick's transaction</h2>
 *
 * {@code JpaTransactionManager} exposes the transaction's JDBC connection to
 * {@code JdbcTemplate}
 * on the same {@code DataSource}, so this insert commits or rolls back together
 * with the tick's
 * dirty-checked updates. {@code GenerationHistoryTests} proves it by failing a tick
 * after the
 * insert and finding no rows.
 */
@Component
public class GenerationHistoryWriter {

    /** The schema name is spliced into SQL, so it is checked to be a bare identifier first. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final String insertSql;

    /**
     * Reads the same property Hibernate uses for its default schema, so the insert
     * follows whatever
     * schema the entities do -- including the isolated schema tests run in.
     */
    public GenerationHistoryWriter(JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to use schema name in SQL: " + schema);
        }
        this.jdbc = jdbc;
        this.insertSql = "insert into " + schema + ".generation_record"
                + " (plant_id, plant_type, tick_number, output_mw, frequency_deviation, recorded_at)"
                + " values (?, ?, ?, ?, ?, ?)";
    }

    public void write(List<GenerationRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        jdbc.batchUpdate(insertSql, records, records.size(), (statement, record) -> {
            statement.setLong(1, record.getPlantId());
            statement.setString(2, record.getPlantType().name());
            statement.setLong(3, record.getTickNumber());
            statement.setDouble(4, record.getOutputMw());
            statement.setDouble(5, record.getFrequencyDeviation());
            statement.setObject(6, record.getRecordedAt().atOffset(ZoneOffset.UTC));
        });
    }
}
