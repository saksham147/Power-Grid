package Billing.api;

import Billing.billing.PlantType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A request to buy a new plant. Deliberately carries {@code plantType} and {@code capacityMw}
 * rather than a price: the amount charged is computed server-side from {@link
 * Billing.billing.PlantPricing}, so a client can never dictate what it pays -- see {@code
 * Billing.api.WalletController#purchasePlant}.
 *
 * <p>
 * No zone is named here: a plant is not owned by any zone (Producer's own {@code PowerPlant} has
 * no zone reference either -- it serves the whole grid), so its price is charged against the
 * single shared Grid wallet rather than requiring the caller to pick a zone to foot the bill.
 *
 * @param plantType  the kind of plant being purchased
 * @param capacityMw the plant's capacity -- must match what is later sent to Producer, or the
 *                   price charged here won't match the plant actually built
 */
public record PlantPurchaseRequest(
        @NotNull PlantType plantType,
        @Positive double capacityMw) {
}
