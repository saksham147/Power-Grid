package Producer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * New name and ratings for an existing unit.
 *
 * <p>
 * There is no {@code type} here on purpose. A plant's {@link
 * Producer.model.PlantType} selects the
 * generation strategy that computes its output, so changing it would replace the
 * machine rather
 * than re-rate it -- and a renewable's stored minimum and setpoint would silently
 * stop being read.
 *
 * <p>
 * All four fields are required, which is why the endpoint is a PUT. The
 * cross-field invariants
 * (minimum and setpoint must both fit under capacity, setpoint must not sit below
 * minimum) are
 * enforced by the entity, so they reject exactly what create rejects.
 */
public record UpgradePlantRequest(
        @NotBlank String name,
        @Positive double capacityMw,
        @PositiveOrZero double minOutputMw,
        @PositiveOrZero double baseOutputMw) {
}
