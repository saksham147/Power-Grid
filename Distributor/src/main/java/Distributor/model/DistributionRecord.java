package Distributor.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One zone's merged supply-and-demand balance for one tick. Insert-only: a record of what
 * happened is never edited.
 *
 * <p>
 * Unlike Producer's {@code generation_record}, this is one row per zone per <em>demand event</em>,
 * not a tick-wide batch of many plants -- so a plain {@code JpaRepository.save} per event is
 * already a single round trip, and there is no identity-batching concern to work around with a
 * hand-rolled JDBC writer.
 */
@Entity
@Table(name = "distribution_record", indexes = {
        @Index(name = "ix_distribution_record_zone_time", columnList = "zone_id, recorded_at"),
        @Index(name = "ix_distribution_record_time", columnList = "recorded_at")
})
public class DistributionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "tick_number", nullable = false)
    private long tickNumber;

    @Column(name = "demand_kw", nullable = false)
    private double demandKw;

    @Column(name = "supplied_kw", nullable = false)
    private double suppliedKw;

    @Column(name = "balance_kw", nullable = false)
    private double balanceKw;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected DistributionRecord() {
        // for JPA
    }

    public DistributionRecord(String zoneId, String zoneName, long tickNumber, double demandKw,
            double suppliedKw, double balanceKw, Instant recordedAt) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.tickNumber = tickNumber;
        this.demandKw = demandKw;
        this.suppliedKw = suppliedKw;
        this.balanceKw = balanceKw;
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

    public double getDemandKw() {
        return demandKw;
    }

    public double getSuppliedKw() {
        return suppliedKw;
    }

    public double getBalanceKw() {
        return balanceKw;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
