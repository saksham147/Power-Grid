package Billing.api.dto;

import java.time.Instant;

import Billing.model.WalletTransaction;

public record TransactionResponse(
        String zoneId,
        String type,
        double amountRupees,
        double balanceAfterRupees,
        Instant occurredAt) {

    public static TransactionResponse from(WalletTransaction transaction) {
        return new TransactionResponse(transaction.getZoneId(), transaction.getType().name(),
                transaction.getAmountRupees(), transaction.getBalanceAfterRupees(), transaction.getOccurredAt());
    }
}
