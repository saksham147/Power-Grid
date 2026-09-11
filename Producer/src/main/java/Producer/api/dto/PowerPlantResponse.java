package Producer.api.dto;

import Producer.model.PlantType;
import Producer.model.PowerPlant;

/**
 * A plant as reported over HTTP.
 *
 * <p>
 * {@link PowerPlant} is a JPA entity, so it is mapped rather than serialised
 * directly: returning
 * the managed instance would tie the wire format to the persistence model and
 * serialise whatever
 * Hibernate happens to have attached to it.
 *
 * @param currentOutputMw result of the most recent tick. Comparing this against
 *                        {@code baseOutputMw} is how droop response is observed
 */
public record PowerPlantResponse(
        Long id,
        String name,
        PlantType type,
        double capacityMw,
        double minOutputMw,
        double baseOutputMw,
        double currentOutputMw,
        boolean active) {

    public static PowerPlantResponse from(PowerPlant plant) {
        return new PowerPlantResponse(
                plant.getId(),
                plant.getName(),
                plant.getType(),
                plant.getCapacityMw(),
                plant.getMinOutputMw(),
                plant.getBaseOutputMw(),
                plant.getCurrentOutputMw(),
                plant.isActive());
    }
}
