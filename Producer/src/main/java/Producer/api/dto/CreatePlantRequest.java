package Producer.api.dto;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A new generating unit.
 *
 * <p>
 * The cross-field rules -- minimum and setpoint both within capacity, setpoint
 * not below the
 * minimum -- are checked in {@link #toEntity()} rather than by annotations,
 * because a plant that
 * violates them makes the droop calculation produce output outside its own
 * clamp range.
 *
 * @param name         display name
 * @param type         which generation strategy drives this plant
 * @param capacityMw   nameplate rating, and the base that droop response is
 *                     scaled against
 * @param minOutputMw  technical minimum. Thermal units cannot turn down below
 *                     roughly 40% of rating
 *                     without tripping offline; renewables carry 0
 * @param baseOutputMw scheduled dispatch setpoint, the value droop adjusts
 *                     around
 */
public record CreatePlantRequest(
        @NotBlank String name,
        @NotNull PlantType type,
        @Positive double capacityMw,
        @PositiveOrZero double minOutputMw,
        @PositiveOrZero double baseOutputMw) {

    /**
     * @throws IllegalArgumentException if the ratings are not internally consistent
     */
    public PowerPlant toEntity() {
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
        return new PowerPlant(name, type, capacityMw, minOutputMw, baseOutputMw);
    }
}
