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
 * A battery or hydrogen storage unit -- charges from surplus, discharges into a deficit,
 * automatically, the same "no player turn" shape every other simulated device in this project has
 * (see {@code Producer.storage.StorageCycleService}). Deliberately its own entity, not a {@link
 * PowerPlant} with a storage {@link PlantType}: storage has no generation strategy, and its own
 * fields (state of charge, charge/discharge rate) have no PowerPlant equivalent.
 */
@Entity
@Table(name = "storage_unit")
public class StorageUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageKind kind;

    /** Energy capacity -- how much this unit can hold, not how fast it can move it. */
    @Column(name = "capacity_kwh", nullable = false)
    private double capacityKwh;

    @Column(name = "max_charge_rate_kw", nullable = false)
    private double maxChargeRateKw;

    @Column(name = "max_discharge_rate_kw", nullable = false)
    private double maxDischargeRateKw;

    /** Current stored energy. Written back once per tick by {@code StorageCycleService}. */
    @Column(name = "state_of_charge_kwh", nullable = false)
    private double stateOfChargeKwh;

    @Column(nullable = false)
    private boolean active = true;

    protected StorageUnit() {
        // for JPA
    }

    public StorageUnit(String name, StorageKind kind, double capacityKwh,
            double maxChargeRateKw, double maxDischargeRateKw) {
        checkRatings(capacityKwh, maxChargeRateKw, maxDischargeRateKw);
        this.name = name;
        this.kind = kind;
        this.capacityKwh = capacityKwh;
        this.maxChargeRateKw = maxChargeRateKw;
        this.maxDischargeRateKw = maxDischargeRateKw;
        // Starts half-charged: neither an immediately-exhausted nor an immediately-full unit,
        // so it can both charge and discharge from the moment it's built.
        this.stateOfChargeKwh = capacityKwh / 2.0;
    }

    /** Re-rates an existing unit. {@code kind} is not a parameter for the same reason {@link
     *  PowerPlant#upgrade} excludes {@code type}: it would substitute a different machine, not
     *  re-rate this one. */
    public void upgrade(String name, double capacityKwh, double maxChargeRateKw, double maxDischargeRateKw) {
        checkRatings(capacityKwh, maxChargeRateKw, maxDischargeRateKw);

        this.name = name;
        this.capacityKwh = capacityKwh;
        this.maxChargeRateKw = maxChargeRateKw;
        this.maxDischargeRateKw = maxDischargeRateKw;

        // A shrink can leave stored energy above the new ceiling -- clamp down, same reasoning
        // PowerPlant.upgrade clamps currentOutputMw.
        this.stateOfChargeKwh = Math.min(this.stateOfChargeKwh, capacityKwh);
    }

    private static void checkRatings(double capacityKwh, double maxChargeRateKw, double maxDischargeRateKw) {
        if (capacityKwh <= 0) {
            throw new IllegalArgumentException("capacityKwh (" + capacityKwh + ") must be positive");
        }
        if (maxChargeRateKw < 0 || maxDischargeRateKw < 0) {
            throw new IllegalArgumentException("charge/discharge rates cannot be negative");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public StorageKind getKind() {
        return kind;
    }

    public double getCapacityKwh() {
        return capacityKwh;
    }

    public double getMaxChargeRateKw() {
        return maxChargeRateKw;
    }

    public double getMaxDischargeRateKw() {
        return maxDischargeRateKw;
    }

    public double getStateOfChargeKwh() {
        return stateOfChargeKwh;
    }

    public void setStateOfChargeKwh(double stateOfChargeKwh) {
        this.stateOfChargeKwh = stateOfChargeKwh;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
