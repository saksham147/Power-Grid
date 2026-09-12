package Producer.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * A new grid frequency deviation, in Hz.
 *
 * <p>
 * Bounded at a quarter of a hertz either way. Real interconnects trip protection
 * long before
 * that, and at 4% droop a 0.25 Hz dip already asks a unit for 12.5% of its
 * rating -- past this the
 * clamp to capacity would be doing all the work and the number would stop meaning
 * anything.
 *
 * <p>
 * Boxed and {@code @NotNull} so a body omitting the field is rejected rather than
 * silently
 * returning the grid to nominal.
 */
public record FrequencyDeviationRequest(
        @NotNull @DecimalMin("-0.25") @DecimalMax("0.25") Double frequencyDeviation) {
}
