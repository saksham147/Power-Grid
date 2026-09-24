package Billing.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads {@code billing_daily_rollup} directly with {@link JdbcTemplate} -- there is no entity to
 * query through Spring Data JPA anymore, since the table is a TimescaleDB continuous aggregate now
 * (see {@link BillingHypertableSetup}), a view Hibernate's {@code ddl-auto} must never try to
 * manage the DDL of. Mirrors {@code Producer.model.GenerationRollupQuery}, trimmed to the one
 * thing anything in this codebase actually reads from the aggregate today: a cumulative kWh total,
 * for {@link Billing.billing.UnlockService}.
 */
@Component
public class BillingRollupQuery {

    /** The schema name is spliced into SQL, so it is checked to be a bare identifier first. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final String table;

    public BillingRollupQuery(JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to use schema name in SQL: " + schema);
        }
        this.jdbc = jdbc;
        this.table = schema + ".billing_daily_rollup";
    }

    /**
     * Every kWh across every zone, for whole-day buckets strictly before {@code cutoffDay}.
     *
     * @param cutoffDay must already be truncated to a day boundary (typically {@link
     *                  Billing.billing.UnlockService}'s own cutoff), or a day whose bucket
     *                  straddles it would be both counted here <em>and</em> counted again by
     *                  {@link BillingRecordRepository#sumKwhSince} reading the same day's still-raw
     *                  rows -- exactly the double-count {@code UnlockService}'s own doc explains.
     */
    public double sumTotalKwhBefore(Instant cutoffDay) {
        Double sum = jdbc.queryForObject(
                "select coalesce(sum(total_kwh), 0) from " + table + " where bucket_day < ?",
                Double.class, cutoffDay.atOffset(ZoneOffset.UTC));
        return sum != null ? sum : 0.0;
    }
}
