package Billing.api.dto;

import java.time.Instant;

import Billing.model.BillingRecord;

public record BillingRecordResponse(
        String zoneId,
        String zoneName,
        long tickNumber,
        double kwh,
        double overageKwh,
        double ratePerKwh,
        double costRupees,
        double overageCostRupees,
        Instant recordedAt) {

    public static BillingRecordResponse from(BillingRecord record) {
        return new BillingRecordResponse(record.getZoneId(), record.getZoneName(), record.getTickNumber(),
                record.getKwh(), record.getOverageKwh(), record.getRatePerKwh(), record.getCostRupees(),
                record.getOverageCostRupees(), record.getRecordedAt());
    }
}
