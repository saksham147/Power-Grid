package Billing.api;

import Billing.billing.PlantType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A request to grow an existing plant's capacity. Charges only the difference between what the
 * plant costs at {@code oldCapacityMw} and at {@code newCapacityMw} -- see {@code
 * Billing.api.WalletController#upgradePlant} -- so a plant that already cost its type's minimum
 * isn't charged again from zero, and shrinking a plant (or leaving it unchanged) costs nothing.
 *
 * @param plantType     the plant's type -- fixed for its lifetime, same as Producer's own rule
 * @param oldCapacityMw the plant's capacity before this edit
 * @param newCapacityMw the plant's capacity after this edit
 */
public record PlantUpgradeRequest(
        @NotNull PlantType plantType,
        @PositiveOrZero double oldCapacityMw,
        @Positive double newCapacityMw) {
}
