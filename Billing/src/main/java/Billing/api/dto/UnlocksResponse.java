package Billing.api.dto;

import java.util.List;

import Billing.billing.PlantType;

/**
 * @param unlockedTypes    every plant type currently purchasable
 * @param cumulativeKwhSold grid-wide energy sold, all time -- see {@code Billing.billing.UnlockService}
 * @param nextUnlock       the next still-locked type and how much further it needs, or {@code null}
 *                          if every type is already unlocked
 */
public record UnlocksResponse(
        List<PlantType> unlockedTypes,
        double cumulativeKwhSold,
        NextUnlock nextUnlock) {

    public record NextUnlock(PlantType type, double kwhRemaining) {
    }
}
