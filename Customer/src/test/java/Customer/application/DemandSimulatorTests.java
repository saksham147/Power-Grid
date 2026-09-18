package Customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import Customer.domain.ConsumerUnit;
import Customer.domain.DemandModel;
import Customer.domain.DemandProfile;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import Customer.domain.ZoneDemand;

/**
 * The use case, driven against fakes.
 *
 * <p>
 * No Spring context, no broker, no Redis: {@code DemandSimulator} depends only on four
 * interfaces, so a lambda is a sufficient adapter for each.
 */
class DemandSimulatorTests {

    private static final SimulationClock CLOCK = new SimulationClock(5, 1);

    private static final List<Zone> THREE_ZONES = List.of(
            new Zone("Z-N", "North"),
            new Zone("Z-C", "Central"),
            new Zone("Z-E", "East"));

    private static final List<ConsumerUnit> THREE_UNITS = List.of(
            new ConsumerUnit("U-N", "Z-N", "North Home", DemandProfile.RESIDENTIAL, 800),
            new ConsumerUnit("U-C", "Z-C", "Central Office", DemandProfile.COMMERCIAL, 1200),
            new ConsumerUnit("U-E", "Z-E", "East Plant", DemandProfile.INDUSTRIAL, 2000));

    /** One call per port per tick, not one per zone -- batching is the point of the port shape. */
    @Test
    void callsEachPortOncePerTickRegardlessOfZoneCount() {
        var publishes = new AtomicInteger();
        var saves = new AtomicInteger();

        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK,
                snapshot -> publishes.incrementAndGet(),
                snapshot -> saves.incrementAndGet());

        for (int tick = 1; tick <= 10; tick++) {
            simulator.tick(tick);
        }

        assertThat(publishes).hasValue(10);
        assertThat(saves).hasValue(10);
        assertThat(simulator.currentTick()).isEqualTo(10);
    }

    @Test
    void aggregatesEveryZoneAndTotalsThem() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);
        DemandSnapshot snapshot = captured.getFirst();

        assertThat(snapshot.zones()).hasSize(3)
                .extracting(ZoneDemand::zoneId)
                .containsExactly("Z-N", "Z-C", "Z-E");
        assertThat(snapshot.totalKw())
                .isEqualTo(snapshot.zones().stream().mapToDouble(ZoneDemand::demandKw).sum());
        assertThat(snapshot.tick()).isEqualTo(1);
        assertThat(snapshot.simulatedTime()).isEqualTo("00:05");
    }

    /** A zone's demand is its units' demand summed, computed the same way {@link DemandModel}
     *  itself would -- not some independent approximation of it. */
    @Test
    void aZonesDemandIsTheSumOfItsOwnUnits() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(100);
        ZoneDemand north = captured.getFirst().zones().stream()
                .filter(z -> z.zoneId().equals("Z-N")).findFirst().orElseThrow();

        var time = CLOCK.timeOfDay(100);
        var day = CLOCK.dayOfWeek(100);
        double expected = DemandModel.demandKw(THREE_UNITS.get(0), time, day);

        assertThat(north.demandKw()).isEqualTo(expected);
        assertThat(north.unitCount()).isEqualTo(1);
    }

    /** A zone nobody has added a unit to yet reports zero, not an error. */
    @Test
    void aZoneWithNoUnitsReportsZeroDemand() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> THREE_ZONES, List::of, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);

        assertThat(captured.getFirst().zones()).allSatisfy(z -> {
            assertThat(z.demandKw()).isZero();
            assertThat(z.unitCount()).isZero();
        });
    }

    /**
     * Both ports are specified never to throw. This is what happens when one breaks
     * that contract:
     * the tick still completes, and the other port still receives the snapshot.
     */
    @Test
    void aThrowingPublisherCostsNeitherTheTickNorTheOtherPort() {
        var saves = new AtomicInteger();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK,
                snapshot -> {
                    throw new IllegalStateException("broker exploded");
                },
                snapshot -> saves.incrementAndGet());

        assertThatCode(() -> simulator.tick(1)).doesNotThrowAnyException();
        assertThat(saves).hasValue(1);
    }

    @Test
    void aThrowingStateStoreCostsNeitherTheTickNorTheOtherPort() {
        var publishes = new AtomicInteger();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK,
                snapshot -> publishes.incrementAndGet(),
                snapshot -> {
                    throw new IllegalStateException("redis exploded");
                });

        assertThatCode(() -> simulator.tick(1)).doesNotThrowAnyException();
        assertThat(publishes).hasValue(1);
    }

    @Test
    void anEmptyFleetOfZonesTicksWithoutFailing() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(List::of, List::of, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);

        assertThat(captured.getFirst().zones()).isEmpty();
        assertThat(captured.getFirst().totalKw()).isZero();
    }

    /**
     * The whole point of reading {@link ZoneRepository#findAll} fresh every tick rather than once
     * at construction: an add or delete is visible on the very next tick, with no restart needed.
     */
    @Test
    void aZoneAddedBetweenTicksAppearsOnTheNextOne() {
        List<Zone> fleet = new ArrayList<>(List.of(THREE_ZONES.get(0)));
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> fleet, () -> THREE_UNITS, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);
        fleet.add(THREE_ZONES.get(1));
        simulator.tick(2);

        assertThat(captured.get(0).zones()).hasSize(1);
        assertThat(captured.get(1).zones()).hasSize(2);
    }

    /** Same idea, one level down: a unit added between ticks changes its zone's demand on the
     *  very next tick, with no restart needed. */
    @Test
    void aUnitAddedBetweenTicksChangesItsZonesDemandOnTheNextOne() {
        List<ConsumerUnit> units = new ArrayList<>(List.of(THREE_UNITS.get(0)));
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> units, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);
        units.add(new ConsumerUnit("U-N2", "Z-N", "North Home B", DemandProfile.RESIDENTIAL, 650));
        simulator.tick(2);

        ZoneDemand firstTickNorth = captured.get(0).zones().stream()
                .filter(z -> z.zoneId().equals("Z-N")).findFirst().orElseThrow();
        ZoneDemand secondTickNorth = captured.get(1).zones().stream()
                .filter(z -> z.zoneId().equals("Z-N")).findFirst().orElseThrow();

        assertThat(firstTickNorth.unitCount()).isEqualTo(1);
        assertThat(secondTickNorth.unitCount()).isEqualTo(2);
        assertThat(secondTickNorth.demandKw()).isGreaterThan(firstTickNorth.demandKw());
    }

    /** The snapshot handed to a port must not be mutable by it. */
    @Test
    void snapshotZonesAreImmutable() {
        var captured = new ArrayList<DemandSnapshot>();
        var simulator = new DemandSimulator(() -> THREE_ZONES, () -> THREE_UNITS, CLOCK, captured::add, snapshot -> {
        });

        simulator.tick(1);

        assertThatCode(() -> captured.getFirst().zones().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
