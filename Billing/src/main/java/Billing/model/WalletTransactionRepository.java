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
}
