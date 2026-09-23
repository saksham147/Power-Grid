package Grid.api.dto;

import java.time.Instant;

import Grid.model.TickRecord;

public record TickHistoryPointResponse(
        long tickNumber,
        String simulatedTime,
        long simulatedDay,
        double frequencyDeviation,
        double totalSupplyKw,
        double totalDemandKw,
        boolean loadExceeded,
        Instant recordedAt) {

    public static TickHistoryPointResponse from(TickRecord record) {
        return new TickHistoryPointResponse(
                record.getTickNumber(),
                record.getSimulatedTime(),
                record.getSimulatedDay(),
                record.getFrequencyDeviation(),
                record.getTotalSupplyKw(),
                record.getTotalDemandKw(),
                record.isLoadExceeded(),
                record.getRecordedAt());
    }
}
