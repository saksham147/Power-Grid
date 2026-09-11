package Producer.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Takes a plant in or out of service.
 *
 * <p>
 * Boxed and {@code @NotNull} rather than a primitive, so an body that omits the
 * field is rejected
 * instead of silently deactivating the plant by defaulting to false.
 *
 * @param active whether the plant should take part in subsequent ticks
 */
public record UpdatePlantActiveRequest(@NotNull Boolean active) {
}
