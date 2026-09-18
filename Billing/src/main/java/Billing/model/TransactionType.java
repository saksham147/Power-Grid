package Billing.model;

/**
 * What kind of wallet movement a {@link WalletTransaction} represents.
 */
public enum TransactionType {
    /** A consumption charge, applied automatically every tick regardless of balance. */
    BILL_DEBIT,
    /** A voluntary spend -- building a new plant -- which is refused rather than allowed to
     *  push the wallet into debt. See {@code Billing.billing.WalletSpendingService}. */
    PLANT_PURCHASE,
    /** A voluntary spend -- growing an existing plant's capacity -- charged only the difference
     *  between its old and new price. See {@code Billing.api.WalletController#upgradePlant}. */
    PLANT_UPGRADE,
    /** A mandatory, unconditional charge -- per-tick upkeep on every active plant, applied
     *  regardless of balance the same way {@code BILL_DEBIT} is. See
     *  {@code Billing.billing.MaintenanceChargeJob}. */
    PLANT_MAINTENANCE,
    /** A credit -- the partial refund paid out when a plant is decommissioned. See
     *  {@code Billing.api.WalletController#decommissionPlant}. */
    PLANT_DECOMMISSION,
    /** A voluntary spend -- building a new storage unit. See
     *  {@code Billing.api.WalletController#purchaseStorage}. */
    STORAGE_PURCHASE
}
