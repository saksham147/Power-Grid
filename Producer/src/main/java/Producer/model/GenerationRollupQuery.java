package Producer.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads {@code generation_rollup} directly with {@link JdbcTemplate} -- the same reason {@link
 * Producer.history.GenerationHistoryWriter} does not go through JPA for {@code
 * generation_record}'s writes, except here it's because there is no entity to query at all: the
 * table is a TimescaleDB continuous aggregate now (see {@link GenerationHypertableSetup}), and
 * Spring Data JPA's {@code @Query} methods need a mapped {@code @Entity} behind them, which a view
 * Hibernate never created and must never try to manage the DDL of.
 */
@Component
public class GenerationRollupQuery {

    /** The schema name is spliced into SQL, so it is checked to be a bare identifier first --
     *  same guard {@link Producer.history.GenerationHistoryWriter} applies to the same input. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final String table;

    public GenerationRollupQuery(JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to use schema name in SQL: " + schema);
        }
        this.jdbc = jdbc;
        this.table = schema + ".generation_rollup";
    }

    /** Newest first, half-open {@code [from, to)} -- same contract as {@code
     *  GenerationRecordRepository#findHistory}, so {@code GenerationHistoryQuery} can treat the
     *  two sources identically once it has picked which one a given range belongs to. */
    public List<GenerationRollupPoint> findHistory(long plantId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                select plant_id, plant_type, bucket_start,
                       avg_output_mw, min_output_mw, max_output_mw,
                       energy_mwh, sample_count, first_tick, last_tick
                from %s
                where plant_id = ? and bucket_start >= ? and bucket_start < ?
                order by bucket_start desc
                limit ?""".formatted(table),
                (rs, rowNum) -> new GenerationRollupPoint(
                        rs.getLong("plant_id"),
                        PlantType.valueOf(rs.getString("plant_type")),
                        rs.getObject("bucket_start", java.time.OffsetDateTime.class).toInstant(),
                        rs.getDouble("avg_output_mw"),
                        rs.getDouble("min_output_mw"),
                        rs.getDouble("max_output_mw"),
                        rs.getDouble("energy_mwh"),
                        rs.getInt("sample_count"),
                        rs.getLong("first_tick"),
                        rs.getLong("last_tick")),
                plantId, from.atOffset(ZoneOffset.UTC), to.atOffset(ZoneOffset.UTC), limit);
    }

    /** Highest {@code last_tick} across every plant, or {@code null} if the aggregate is empty --
     *  same contract as {@code GenerationRecordRepository#findMaxTickNumber}. */
    public Long findMaxLastTick() {
        return jdbc.queryForObject("select max(last_tick) from %s".formatted(table), Long.class);
    }
}
