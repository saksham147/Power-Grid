package Billing.api.dto;

import java.time.Instant;

/**
 * What a plant purchase or upgrade actually charged against the shared Grid wallet -- the
 * server-computed price, not an echo of a request. {@code amountRupees} is 0 for an upgrade that
 * didn't raise the plant's price (e.g. a downgrade).
 */
public record PlantSpendResponse(
        String walletId,
        String walletName,
        double amountRupees,
        double balanceRupees,
        Instant occurredAt) {
}
