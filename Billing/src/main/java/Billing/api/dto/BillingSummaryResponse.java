package Billing.api.dto;

/**
 * Grid-wide revenue vs. spend, all time -- the "cost efficiency" figure the frontend's live
 * scoreboard panel reads. Not persisted anywhere of its own; both sides are summed fresh from
 * {@code WalletTransaction} on every request.
 *
 * @param revenueRupees every {@code BILL_DEBIT} ever applied -- what consumption has earned
 * @param spendRupees   every {@code PLANT_PURCHASE}/{@code PLANT_UPGRADE}/{@code
 *                      PLANT_MAINTENANCE}/{@code STORAGE_PURCHASE} ever charged -- what building
 *                      and running the fleet has cost
 */
public record BillingSummaryResponse(double revenueRupees, double spendRupees) {
}
