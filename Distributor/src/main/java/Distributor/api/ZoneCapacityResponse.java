package Distributor.api;

import java.time.Instant;

import Distributor.model.ZoneCapacity;

/**
 * @param currentDemandKw the zone's latest known demand from {@code GridStateTracker} -- 0 if it
 *                        has never reported -- so the UI can show draw against capacity live
 * @param overCapacity    whether current demand exceeds the assigned capacity right now
 */
public record ZoneCapacityResponse(
        String zoneId,
        String zoneName,
        double capacityKw,
        double currentDemandKw,
        boolean overCapacity,
        Instant updatedAt) {

    public static ZoneCapacityResponse from(ZoneCapacity capacity, double currentDemandKw) {
        return new ZoneCapacityResponse(
                capacity.getZoneId(), capacity.getZoneName(), capacity.getCapacityKw(),
                currentDemandKw, currentDemandKw > capacity.getCapacityKw(), capacity.getUpdatedAt());
    }
}
