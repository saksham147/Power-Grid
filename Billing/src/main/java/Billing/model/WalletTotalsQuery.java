package Billing.model;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Every wallet's lifetime total per transaction type, read from {@code wallet_daily_totals}
 * instead of scanning the raw ledger -- see {@link WalletTransactionHypertableSetup} for why. Feeds
 * both halves of {@code Billing.billing.MoneyFlowService}'s all-time figures, the same role {@code
 * WalletTransactionRepository#totalsByZoneAndType} used to play before the ledger was hypertabled.
 *
 * <p>
 * {@code JdbcTemplate}, not Spring Data JPA, for the same reason {@code
 * Billing.model.BillingRollupQuery}/{@code Producer.model.GenerationRollupQuery} aren't JPA
 * repositories: a continuous aggregate is a view, not a table Hibernate can map an {@code @Entity}
 * onto.
 */
@Component
public class WalletTotalsQuery {

    private final JdbcTemplate jdbc;
    private final String table;

    public WalletTotalsQuery(JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        this.jdbc = jdbc;
        this.table = schema + ".wallet_daily_totals";
    }

    public List<WalletZoneTypeTotal> totalsByZoneAndType() {
        return jdbc.query(
                "select zone_id, type, sum(total_rupees) as amount_rupees from " + table + " group by zone_id, type",
                (rs, rowNum) -> new WalletZoneTypeTotal(
                        rs.getString("zone_id"),
                        TransactionType.valueOf(rs.getString("type")),
                        rs.getDouble("amount_rupees")));
    }
}
