package Billing.model;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /** Newest first. */
    @Query("select t from WalletTransaction t where t.zoneId = :zoneId order by t.occurredAt desc")
    List<WalletTransaction> findByZone(String zoneId, Limit limit);

    /** Grid-wide sum across every wallet, for whichever transaction types the caller groups
     *  together as one figure -- see {@code Billing.api.WalletController#summary}, which sums
     *  {@code BILL_DEBIT} for revenue and every spend type for total spend. */
    @Query("select coalesce(sum(t.amountRupees), 0) from WalletTransaction t where t.type in :types")
    double sumByTypes(Collection<TransactionType> types);

    /** One (wallet, transaction type) pair's lifetime total -- see {@link #totalsByZoneAndType}. */
    interface TransactionTotalRow {
        String getZoneId();

        TransactionType getType();

        double getAmountRupees();
    }

    /**
     * Every wallet's lifetime total per transaction type, in one pass over the ledger. Feeds both
     * halves of {@code Billing.billing.MoneyFlowService}'s all-time figures -- revenue per zone
     * (the {@code BILL_DEBIT} rows) and fleet spend by category (the plant/storage rows) -- so the
     * endpoint scans the table once, not once per figure.
     */
    @Query("""
            select t.zoneId as zoneId, t.type as type, sum(t.amountRupees) as amountRupees
            from WalletTransaction t group by t.zoneId, t.type""")
    List<TransactionTotalRow> totalsByZoneAndType();
}
