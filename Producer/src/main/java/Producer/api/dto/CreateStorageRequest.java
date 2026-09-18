package Producer.api.dto;

import Producer.model.StorageKind;
import Producer.model.StorageUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateStorageRequest(
        @NotBlank String name,
        @NotNull StorageKind kind,
        @Positive double capacityKwh,
        @PositiveOrZero double maxChargeRateKw,
        @PositiveOrZero double maxDischargeRateKw) {

    public StorageUnit toEntity() {
        return new StorageUnit(name, kind, capacityKwh, maxChargeRateKw, maxDischargeRateKw);
    }
}
