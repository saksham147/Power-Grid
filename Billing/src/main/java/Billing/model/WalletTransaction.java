package Billing.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One wallet ledger movement. Insert-only, and deliberately separate from {@link BillingRecord}:
 * a billing record is <em>why</em> a charge happened (the kWh and rate behind it); this is the
 * wallet movement itself. They carry near-identical numbers today because billing is the only
 * source of transactions so far, but a future non-billing debit (spending the wallet on a new
 * plant) will have a transaction with no billing record behind it -- keeping them separate now
 * avoids having to split them apart later.
 *
 * <p>
 * The primary key below is single-column -- that's Hibernate's starting point on a fresh database,
 * not the final shape. {@link WalletTransactionHypertableSetup} replaces it with a composite
 * version that includes {@code occurred_at} (TimescaleDB requires the partitioning column in every
 * unique constraint on a hypertable) the first time it runs. {@code id} stays the JPA {@code @Id}
 * regardless -- only the database-level constraint shape changes. Unlike every other hypertabled
 * table in this project, no retention policy is ever added to this one -- see that class's own doc
 * for why: this is the ledger, and nothing here is ever pruned.
 */
@Entity
@Table(name = "wallet_transaction", indexes = @Index(name = "ix_wallet_transaction_zone_time", columnList = "zone_id, occurred_at"))
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount_rupees", nullable = false)
    private double amountRupees;

    @Column(name = "balance_after_rupees", nullable = false)
    private double balanceAfterRupees;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WalletTransaction() {
        // for JPA
    }

    public WalletTransaction(String zoneId, TransactionType type, double amountRupees, double balanceAfterRupees,
            Instant occurredAt) {
        this.zoneId = zoneId;
        this.type = type;
        this.amountRupees = amountRupees;
        this.balanceAfterRupees = balanceAfterRupees;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmountRupees() {
        return amountRupees;
    }

    public double getBalanceAfterRupees() {
        return balanceAfterRupees;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
