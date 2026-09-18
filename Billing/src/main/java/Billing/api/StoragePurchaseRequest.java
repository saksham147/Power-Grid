package Billing.api;

import Billing.billing.StorageKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Mirrors {@code PlantPurchaseRequest}: no price, no owning zone -- storage serves the whole
 *  grid the same way a plant does, so this always charges the shared Grid wallet. */
public record StoragePurchaseRequest(
        @NotNull StorageKind kind,
        @Positive double capacityKwh) {
}
