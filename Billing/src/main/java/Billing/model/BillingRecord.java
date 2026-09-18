package Billing.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One zone's billing charge for one tick. Insert-only: a record of what was billed is never
 * edited.
 *
 * <p>
 * The unique {@code (zone_id, tick_number)} constraint is the idempotency guard against Kafka's
 * at-least-once delivery of {@code customer.demand} -- see {@link Billing.billing.BillingLedger}.
 */
@Entity
@Table(name = "billing_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_billing_record_zone_tick", columnNames = { "zone_id", "tick_number" }),
        indexes = @Index(name = "ix_billing_record_zone_time", columnList = "zone_id, recorded_at"))
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "tick_number", nullable = false)
    private long tickNumber;

    @Column(name = "kwh", nullable = false)
    private double kwh;

    /** How much of {@code kwh} was above the zone's assigned capacity -- 0 for an uncapped zone,
     *  or one that drew within its capacity this tick. See {@code Billing.billing.BillingCycleService}. */
    @Column(name = "overage_kwh", nullable = false)
    private double overageKwh;

    @Column(name = "rate_per_kwh", nullable = false)
    private double ratePerKwh;

    @Column(name = "cost_rupees", nullable = false)
    private double costRupees;

    /** The slice of {@code costRupees} charged at the overage rate -- already included in it,
     *  broken out so the surcharge stays visible without reconstructing it from a rate that may
     *  have since changed. */
    @Column(name = "overage_cost_rupees", nullable = false)
    private double overageCostRupees;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected BillingRecord() {
        // for JPA
    }

    public BillingRecord(String zoneId, String zoneName, long tickNumber, double kwh, double overageKwh,
            double ratePerKwh, double costRupees, double overageCostRupees, Instant recordedAt) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.tickNumber = tickNumber;
        this.kwh = kwh;
        this.overageKwh = overageKwh;
        this.ratePerKwh = ratePerKwh;
        this.costRupees = costRupees;
        this.overageCostRupees = overageCostRupees;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public long getTickNumber() {
        return tickNumber;
    }

    public double getKwh() {
        return kwh;
    }

    public double getOverageKwh() {
        return overageKwh;
    }

    public double getRatePerKwh() {
        return ratePerKwh;
    }

    public double getCostRupees() {
        return costRupees;
    }

    public double getOverageCostRupees() {
        return overageCostRupees;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
