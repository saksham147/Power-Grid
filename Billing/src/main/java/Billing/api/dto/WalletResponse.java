package Billing.api.dto;

import java.time.Instant;

import Billing.model.Wallet;

public record WalletResponse(String zoneId, String zoneName, double balanceRupees, Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getZoneId(), wallet.getZoneName(), wallet.getBalanceRupees(),
                wallet.getUpdatedAt());
    }
}
