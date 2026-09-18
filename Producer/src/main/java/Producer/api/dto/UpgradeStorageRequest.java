package Producer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** No {@code kind} here, for the same reason {@code UpgradePlantRequest} excludes {@code type}:
 *  it would substitute a different machine, not re-rate this one. */
public record UpgradeStorageRequest(
        @NotBlank String name,
        @Positive double capacityKwh,
        @PositiveOrZero double maxChargeRateKw,
        @PositiveOrZero double maxDischargeRateKw) {
}
