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
        @DefaultValue List<ZoneConfig> zones) {

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

    /**
     * A zone as configured. Maps onto the domain's {@code Zone}, which is where the
     * meaning of
     * each field is documented -- particularly why there are two variabilities.
     */
    public record ZoneConfig(
            String id,
            String name,
            long customers,
            DemandProfile profile,
            double baseKwPerCustomer,
            @DefaultValue("0.30") double customerVariability,
            @DefaultValue("0.05") double zoneVariability) {
    }
}
