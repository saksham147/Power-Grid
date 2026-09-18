package Billing.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A zone's running balance. One row per zone, created lazily the first time that zone is billed.
 *
 * <p>
 * {@code balanceRupees} is allowed to go negative. A real ledger permits debt rather than silently
 * clamping at zero and losing track of what a zone actually owes; nothing currently reads a
 * negative balance as a reason to refuse anything, since spending the wallet (on a new plant, say)
 * is not part of this service yet.
 */
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "balance_rupees", nullable = false)
    private double balanceRupees;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
        // for JPA
    }

    public Wallet(String zoneId, String zoneName, double balanceRupees) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.balanceRupees = balanceRupees;
        this.updatedAt = Instant.now();
    }

    public void debit(double amountRupees) {
        this.balanceRupees -= amountRupees;
        this.updatedAt = Instant.now();
    }

    /** The symmetric counterpart to {@link #debit} -- a refund (decommissioning a plant) or any
     *  other credit. Unlike a spend, a credit can never be refused, so there is no balance check
     *  here; the caller decides whether the credit itself is warranted. */
    public void credit(double amountRupees) {
        this.balanceRupees += amountRupees;
        this.updatedAt = Instant.now();
    }

    /** Zones can be renamed in Customer; each charge keeps this in step rather than going stale. */
    public void renameTo(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public double getBalanceRupees() {
        return balanceRupees;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
