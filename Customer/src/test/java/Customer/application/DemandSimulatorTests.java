package Customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import Customer.domain.DemandProfile;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import Customer.domain.ZoneDemand;

/**
 * The use case, driven against fakes.
 *
 * <p>
 * No Spring context, no broker, no Redis: {@code DemandSimulator} depends only
 * on two interfaces,
 * so a lambda is a sufficient adapter.
 */
class DemandSimulatorTests {

    private static final SimulationClock CLOCK = new SimulationClock(5, 1);

    private static final List<Zone> THREE_ZONES = List.of(
            new Zone("Z-N", "North", 250_000, DemandProfile.RESIDENTIAL, 1.1, 0.35, 0.06),
            new Zone("Z-C", "Central", 40_000, DemandProfile.COMMERCIAL, 7.5, 0.25, 0.05),
            new Zone("Z-E", "East", 800, DemandProfile.INDUSTRIAL, 240.0, 0.12, 0.03));

    /** One call per port per tick, not one per zone -- batching is the point of the port shape. */
    @Test
    void callsEachPortOncePerTickRegardlessOfZoneCount() {
        var publishes = new AtomicInteger();
        var saves = new AtomicInteger();

        var simulator = new DemandSimulator(THREE_ZONES, CLOCK,
                snapshot -> publishes.incrementAndGet(),
                snapshot -> saves.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            simulator.tick();
        }

        assertThat(publishes).hasValue(10);
        assertThat(saves).hasValue(10);
        assertThat(simulator.currentTick()).isEqualTo(10);
    }

    @Test
    void aggregatesEveryZoneAndTotalsThem() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(THREE_ZONES, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick();
        DemandSnapshot snapshot = captured.getFirst();

        assertThat(snapshot.zones()).hasSize(3)
                .extracting(ZoneDemand::zoneId)
                .containsExactly("Z-N", "Z-C", "Z-E");
        assertThat(snapshot.totalKw())
                .isEqualTo(snapshot.zones().stream().mapToDouble(ZoneDemand::demandKw).sum());
        assertThat(snapshot.tick()).isEqualTo(1);
        assertThat(snapshot.simulatedTime()).isEqualTo("00:05");
    }

    /**
     * Both ports are specified never to throw. This is what happens when one breaks
     * that contract:
     * the tick still completes, and the other port still receives the snapshot.
     */
    @Test
    void aThrowingPublisherCostsNeitherTheTickNorTheOtherPort() {
        var saves = new AtomicInteger();
        var simulator = new DemandSimulator(THREE_ZONES, CLOCK,
                snapshot -> {
                    throw new IllegalStateException("broker exploded");
                },
                snapshot -> saves.incrementAndGet());

        assertThatCode(simulator::tick).doesNotThrowAnyException();
        assertThat(saves).hasValue(1);
    }

    @Test
    void aThrowingStateStoreCostsNeitherTheTickNorTheOtherPort() {
        var publishes = new AtomicInteger();
        var simulator = new DemandSimulator(THREE_ZONES, CLOCK,
                snapshot -> publishes.incrementAndGet(),
                snapshot -> {
                    throw new IllegalStateException("redis exploded");
                });

        assertThatCode(simulator::tick).doesNotThrowAnyException();
        assertThat(publishes).hasValue(1);
    }

    @Test
    void anEmptyFleetOfZonesTicksWithoutFailing() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(List.of(), CLOCK, captured::add, snapshot -> {
        });

        simulator.tick();

        assertThat(captured.getFirst().zones()).isEmpty();
        assertThat(captured.getFirst().totalKw()).isZero();
    }

    /** The simulator must not alias a caller's list, or a later mutation would change the fleet. */
    @Test
    void copiesTheZoneListItWasGiven() {
        var mutable = new ArrayList<>(THREE_ZONES);
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(mutable, CLOCK, captured::add, snapshot -> {
        });

        mutable.clear();
        simulator.tick();

        assertThat(captured.getFirst().zones()).hasSize(3);
    }

    /** The snapshot handed to a port must not be mutable by it. */
    @Test
    void snapshotZonesAreImmutable() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(THREE_ZONES, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick();

        assertThatCode(() -> captured.getFirst().zones().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
