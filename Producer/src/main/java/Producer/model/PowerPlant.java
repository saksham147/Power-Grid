package Producer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single generating unit. Producer is the sole owner of this table.
 *
 * <p>
 * The schema is set by {@code hibernate.default_schema} rather than being
 * pinned here, so the
 * entity stays portable across environments.
 */
@Entity
@Table(name = "power_plant")
public class PowerPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlantType type;

    /** Nameplate rating. Also the base that droop response is scaled against. */
    @Column(name = "capacity_mw", nullable = false)
    private double capacityMw;

    /**
     * Technical minimum. A thermal unit cannot be turned down below roughly 40% of
     * rating without
     * tripping offline; renewables carry 0 here.
     */
    @Column(name = "min_output_mw", nullable = false)
    private double minOutputMw;

    /**
     * The scheduled dispatch setpoint — what the unit was told to produce before
     * any frequency
     * response. Droop adjusts around this value, which is why it has to exist: with
     * no setpoint
     * there is no headroom to respond downward.
     */
    @Column(name = "base_output_mw", nullable = false)
    private double baseOutputMw;

    /** Result of the most recent tick. Written back once per tick. */
    @Column(name = "current_output_mw", nullable = false)
    private double currentOutputMw;

    @Column(nullable = false)
    private boolean active = true;

    protected PowerPlant() {
        // for JPA
    }

    public PowerPlant(String name, PlantType type, double capacityMw, double minOutputMw, double baseOutputMw) {
        this.name = name;
        this.type = type;
        this.capacityMw = capacityMw;
        this.minOutputMw = minOutputMw;
        this.baseOutputMw = baseOutputMw;
        this.currentOutputMw = baseOutputMw;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PlantType getType() {
        return type;
    }

    public double getCapacityMw() {
        return capacityMw;
    }

    public double getMinOutputMw() {
        return minOutputMw;
    }

    public double getBaseOutputMw() {
        return baseOutputMw;
    }

    public double getCurrentOutputMw() {
        return currentOutputMw;
    }

    public void setCurrentOutputMw(double currentOutputMw) {
        this.currentOutputMw = currentOutputMw;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
