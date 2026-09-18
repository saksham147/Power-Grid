package Distributor.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The power capacity Distributor has assigned a zone -- a billing-relevant ceiling, not a hard
 * limit on delivery: {@code DistributionService} still allocates supply purely by demand share,
 * unchanged by this. A zone with no row here is uncapped -- billed at the normal rate regardless
 * of how much it draws, exactly as before this feature existed.
 */
@Entity
@Table(name = "zone_capacity")
public class ZoneCapacity {

    @Id
    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "capacity_kw", nullable = false)
    private double capacityKw;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ZoneCapacity() {
        // for JPA
    }

    public ZoneCapacity(String zoneId, String zoneName, double capacityKw) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.capacityKw = capacityKw;
        this.updatedAt = Instant.now();
    }

    public void update(String zoneName, double capacityKw) {
        this.zoneName = zoneName;
        this.capacityKw = capacityKw;
        this.updatedAt = Instant.now();
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public double getCapacityKw() {
        return capacityKw;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
