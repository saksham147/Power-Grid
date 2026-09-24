package Billing.model;

/**
 * One (wallet, transaction type) pair's lifetime total -- see {@link WalletTotalsQuery}.
 */
public record WalletZoneTypeTotal(String zoneId, TransactionType type, double amountRupees) {
}
