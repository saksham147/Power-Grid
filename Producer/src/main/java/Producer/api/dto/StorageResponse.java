package Producer.api.dto;

import Producer.model.StorageKind;
import Producer.model.StorageUnit;

public record StorageResponse(
        Long id,
        String name,
        StorageKind kind,
        double capacityKwh,
        double maxChargeRateKw,
        double maxDischargeRateKw,
        double stateOfChargeKwh,
        boolean active) {

    public static StorageResponse from(StorageUnit unit) {
        return new StorageResponse(
                unit.getId(),
                unit.getName(),
                unit.getKind(),
                unit.getCapacityKwh(),
                unit.getMaxChargeRateKw(),
                unit.getMaxDischargeRateKw(),
                unit.getStateOfChargeKwh(),
                unit.isActive());
    }
}
