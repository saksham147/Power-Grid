package Customer.api;

import Customer.domain.ConsumerUnit;

/**
 * A unit as reported over HTTP, with its live demand computed at the current simulated moment --
 * see {@code UnitController}.
 */
public record UnitResponse(
        String unitId,
        String zoneId,
        String name,
        String type,
        double capacityKw,
        double demandKw) {

    public static UnitResponse from(ConsumerUnit unit, double demandKw) {
        return new UnitResponse(unit.unitId(), unit.zoneId(), unit.name(), unit.type().name(),
                unit.capacityKw(), demandKw);
    }
}
