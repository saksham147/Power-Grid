package Customer.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import Customer.domain.DemandProfile;

/**
 * Everything tunable, bound from {@code customer.*}.
 *
 * <p>
 * The {@code @DefaultValue}s duplicate what {@code application.yml} sets on
 * purpose, so the
 * service still starts sanely if a block is removed from the YAML.
 */
@ConfigurationProperties("customer")
public record CustomerProperties(
        @DefaultValue Simulation simulation,
        @DefaultValue List<ZoneConfig> zones,
        @DefaultValue List<UnitConfig> units) {

    /**
     * @param simulatedMinutesPerTick       simulated time one tick covers; must divide
     *                                      1440
     * @param realSecondsPerSimulatedMinute the simulation speed. 1 means one real
     *                                      second per simulated minute
     */
    public record Simulation(
            @DefaultValue("5") int simulatedMinutesPerTick,
            @DefaultValue("1") int realSecondsPerSimulatedMinute) {
    }

    /** A zone as configured: just an id and a name now that a zone carries no demand itself. */
    public record ZoneConfig(String id, String name) {
    }

    /**
     * A unit as configured. Maps onto the domain's {@code ConsumerUnit}, which is where the meaning
     * of each field is documented.
     */
    public record UnitConfig(String unitId, String zoneId, String name, DemandProfile type, double capacityKw) {
    }
}
