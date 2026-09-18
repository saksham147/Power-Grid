package Distributor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateZoneCapacityRequest(
        @NotBlank String zoneName,
        @Positive double capacityKw) {
}
