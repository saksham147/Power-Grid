package Producer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;

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

    /**
     * Cumulative energy this unit has generated, in megawatt hours.
     *
     * <p>
     * Power is what a tick reports; energy is what it adds up to. Persisted rather
     * than summed in
     * memory so the figure survives a restart, and per plant rather than fleet-wide
     * so the table
     * can show which unit actually produced what.
     *
     * <p>
     * {@code @ColumnDefault} is load-bearing, not decoration. Postgres refuses
     * {@code ADD COLUMN ... NOT NULL} on a table that already has rows unless a
     * default is supplied,
     * so without it Hibernate's schema update fails, the column is never created,
     * and every
     * subsequent tick dies on a missing column.
     */
    @ColumnDefault("0")
    @Column(name = "energy_mwh", nullable = false)
    private double energyMwh;

    @Column(nullable = false)
    private boolean active = true;

    protected PowerPlant() {
        // for JPA
    }

    public PowerPlant(String name, PlantType type, double capacityMw, double minOutputMw, double baseOutputMw) {
        checkRatings(capacityMw, minOutputMw, baseOutputMw);
        this.name = name;
        this.type = type;
        this.capacityMw = capacityMw;
        this.minOutputMw = minOutputMw;
        this.baseOutputMw = baseOutputMw;
        this.currentOutputMw = baseOutputMw;
    }

    /**
     * Re-rates an existing unit: new name and new ratings, applied together.
     *
     * <p>
     * One method rather than four setters, so a plant can never be halfway through a
     * re-rating --
     * a raised minimum with the old capacity still in place would be a state no real
     * unit has, and
     * the generation strategies would happily compute against it.
     *
     * <p>
     * {@code type} is deliberately not a parameter. It selects which strategy runs,
     * so changing it
     * would not re-rate this unit, it would substitute a different machine into the
     * same row.
     *
     * @throws IllegalArgumentException if the new ratings are not internally
     *                                  consistent
     */
    public void upgrade(String name, double capacityMw, double minOutputMw, double baseOutputMw) {
        checkRatings(capacityMw, minOutputMw, baseOutputMw);

        this.name = name;
        this.capacityMw = capacityMw;
        this.minOutputMw = minOutputMw;
        this.baseOutputMw = baseOutputMw;

        // A downgrade can leave the last tick's output above the new ceiling. Only the
        // upper bound is clamped: an idle or inactive unit sitting below its technical
        // minimum is a real state, so forcing it up to the new minimum would invent output
        // the plant is not producing.
        this.currentOutputMw = Math.min(this.currentOutputMw, capacityMw);
    }

    /**
     * The rating invariants, in one place so the constructor and {@link #upgrade}
     * cannot drift apart.
     */
    private static void checkRatings(double capacityMw, double minOutputMw, double baseOutputMw) {
        if (minOutputMw > capacityMw) {
            throw new IllegalArgumentException(
                    "minOutputMw (" + minOutputMw + ") cannot exceed capacityMw (" + capacityMw + ")");
        }
        if (baseOutputMw > capacityMw) {
            throw new IllegalArgumentException(
                    "baseOutputMw (" + baseOutputMw + ") cannot exceed capacityMw (" + capacityMw + ")");
        }
        if (baseOutputMw < minOutputMw) {
            throw new IllegalArgumentException(
                    "baseOutputMw (" + baseOutputMw + ") cannot be below minOutputMw (" + minOutputMw + ")");
        }
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

    public double getEnergyMwh() {
        return energyMwh;
    }

    /** Adds one tick's worth of generation. Accumulate-only: there is no setter. */
    public void addEnergy(double mwh) {
        this.energyMwh += mwh;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
