package Billing.api;

import Billing.billing.PlantType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A request to decommission a plant and receive a partial refund, computed the same way a
 * purchase's price is: server-side, from {@link Billing.billing.PlantPricing}, keyed on the same
 * {@code type}/{@code capacityMw} the plant was actually built with -- see {@code
 * Billing.api.WalletController#decommissionPlant}.
 */
public record PlantDecommissionRequest(
        @NotNull PlantType plantType,
        @Positive double capacityMw) {
}
