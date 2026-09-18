package Billing.billing;

/** A wallet -- a zone's, or the shared Grid wallet -- was asked to spend more than it holds. */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String walletId, double balanceRupees, double amountRupees) {
        // %.2f, not Double.toString(): a wallet run deep into debt over thousands of ticks
        // prints in scientific notation by default (-3.92E8), which is correct but unreadable
        // in a message meant for a human deciding whether they can afford something.
        super("Wallet %s has ₹%.2f but this costs ₹%.2f".formatted(walletId, balanceRupees, amountRupees));
    }
}
