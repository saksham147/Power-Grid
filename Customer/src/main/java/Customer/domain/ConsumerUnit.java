package Customer.domain;

/**
 * A single house, factory or commercial building -- the atomic demand-generating entity in this
 * service, and the child of exactly one {@link Zone}.
 *
 * <p>
 * This replaces the population-based model {@link Zone} used to carry directly (a customer count
 * and a per-customer average): that model existed to make a whole zone cheap to simulate
 * regardless of how many anonymous customers it represented, by collapsing them into one Gaussian
 * draw per tick. A handful of named, individually meaningful buildings is a different problem --
 * each one is significant on its own, the way a {@code Producer.model.PowerPlant} is -- so it gets
 * the same treatment: a rated capacity, a type that selects how that capacity behaves, and a
 * closed-form formula in {@link DemandModel} instead of population statistics.
 *
 * @param unitId     stable identifier
 * @param zoneId     the zone this unit belongs to
 * @param name       display name, e.g. {@code "North Homes A"}
 * @param type       which demand shape this unit follows over a day and week -- residential,
 *                   commercial or industrial
 * @param capacityKw this unit's rated demand, in kW, at a profile factor of 1.0
 */
public record ConsumerUnit(String unitId, String zoneId, String name, DemandProfile type, double capacityKw) {

    public ConsumerUnit {
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId must not be blank");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("unit " + unitId + " must belong to a zone");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("unit " + unitId + " must have a name");
        }
        if (type == null) {
            throw new IllegalArgumentException("unit " + unitId + " must have a type");
        }
        if (capacityKw <= 0) {
            throw new IllegalArgumentException("unit " + unitId + " must have a positive capacityKw");
        }
    }
}
