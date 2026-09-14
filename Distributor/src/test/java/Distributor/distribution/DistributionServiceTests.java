package Distributor.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import Distributor.event.ProducerOutputEvent;
import Distributor.event.ZoneDemandEvent;

/**
 * The use case, driven against fakes -- no Spring context, no broker, no database:
 * {@code DistributionService} depends only on {@link GridStateTracker} (a plain in-memory class)
 * and two port interfaces, so a lambda is a sufficient adapter for each.
 */
class DistributionServiceTests {

    private static ZoneDemandEvent demand(String zoneId, double demandKw) {
        return new ZoneDemandEvent(zoneId, zoneId + "-name", 1, "00:05", 1000, demandKw, Instant.now());
    }

    private static ProducerOutputEvent supply(long plantId, double outputMw) {
        return new ProducerOutputEvent(plantId, 1, outputMw, Instant.now());
    }

    /**
     * Each event allocates against the total demand known so far, not a completed tick's worth --
     * see {@link DistributionService}. The first zone to report in a round is therefore allocated
     * the whole of whatever total demand is known at that instant; the split only settles to the
     * true ratio once every zone has reported at least once.
     */
    @Test
    void allocatesSupplyProportionallyToEachZonesShareOfDemandOnceEveryZoneHasReported() {
        var service = new DistributionService(new GridStateTracker(), d -> {
        }, d -> {
        });

        // 1000 kW of total supply (1 MW), split 3:1 between two zones by demand.
        service.onProducerOutput(supply(1, 1.0));
        service.onZoneDemand(demand("Z-N", 750));
        service.onZoneDemand(demand("Z-S", 250));

        // A second round: both zones' demand is now fully known, so the 3:1 split is exact.
        ZoneDistribution north = service.onZoneDemand(demand("Z-N", 750));
        ZoneDistribution south = service.onZoneDemand(demand("Z-S", 250));

        assertThat(north.suppliedKw()).isCloseTo(750.0, within(1e-9));
        assertThat(south.suppliedKw()).isCloseTo(250.0, within(1e-9));
        assertThat(north.balanceKw()).isCloseTo(0.0, within(1e-9));
        assertThat(south.balanceKw()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void convertsProducerMegawattsToCustomerKilowattsBeforeAllocating() {
        var service = new DistributionService(new GridStateTracker(), d -> {
        }, d -> {
        });

        service.onProducerOutput(supply(1, 2.0)); // 2 MW = 2000 kW
        ZoneDistribution zone = service.onZoneDemand(demand("Z-N", 500));

        // The only zone: gets the whole 2000 kW regardless of its own demand figure.
        assertThat(zone.suppliedKw()).isCloseTo(2000.0, within(1e-9));
        assertThat(zone.balanceKw()).isCloseTo(1500.0, within(1e-9));
    }

    @Test
    void zeroTotalDemandAllocatesNothingRatherThanDividingByZero() {
        var service = new DistributionService(new GridStateTracker(), d -> {
        }, d -> {
        });

        service.onProducerOutput(supply(1, 1.0));

        assertThatCode(() -> service.onZoneDemand(demand("Z-N", 0)))
                .doesNotThrowAnyException();
        ZoneDistribution zone = service.onZoneDemand(demand("Z-N", 0));
        assertThat(zone.suppliedKw()).isZero();
    }

    @Test
    void aSecondReadingFromTheSamePlantReplacesRatherThanAddsToItsOutput() {
        var service = new DistributionService(new GridStateTracker(), d -> {
        }, d -> {
        });

        service.onProducerOutput(supply(1, 1.0));
        service.onProducerOutput(supply(1, 3.0)); // same plant, a later tick's reading
        ZoneDistribution zone = service.onZoneDemand(demand("Z-N", 100));

        assertThat(zone.suppliedKw()).isCloseTo(3000.0, within(1e-9));
    }

    @Test
    void bothPortsAreCalledOncePerDemandEvent() {
        var logs = new AtomicInteger();
        var publishes = new AtomicInteger();
        var service = new DistributionService(new GridStateTracker(),
                d -> logs.incrementAndGet(),
                d -> publishes.incrementAndGet());

        service.onZoneDemand(demand("Z-N", 100));
        service.onZoneDemand(demand("Z-S", 200));

        assertThat(logs).hasValue(2);
        assertThat(publishes).hasValue(2);
    }

    /**
     * Both ports are specified never to throw. This is what happens when one breaks that contract:
     * the merge still completes, and the other port still receives it.
     */
    @Test
    void aThrowingLoggerCostsNeitherTheMergeNorThePublish() {
        var publishes = new AtomicInteger();
        var service = new DistributionService(new GridStateTracker(),
                d -> {
                    throw new IllegalStateException("database exploded");
                },
                d -> publishes.incrementAndGet());

        assertThatCode(() -> service.onZoneDemand(demand("Z-N", 100))).doesNotThrowAnyException();
        assertThat(publishes).hasValue(1);
    }

    @Test
    void aThrowingPublisherCostsNeitherTheMergeNorTheLog() {
        var logs = new AtomicInteger();
        var service = new DistributionService(new GridStateTracker(),
                d -> logs.incrementAndGet(),
                d -> {
                    throw new IllegalStateException("broker exploded");
                });

        assertThatCode(() -> service.onZoneDemand(demand("Z-N", 100))).doesNotThrowAnyException();
        assertThat(logs).hasValue(1);
    }

    @Test
    void carriesTheDemandEventsOwnTickAndTimestampThrough() {
        var captured = new ArrayList<ZoneDistribution>();
        var service = new DistributionService(new GridStateTracker(), d -> {
        }, captured::add);
        Instant at = Instant.parse("2026-01-01T00:00:00Z");

        service.onZoneDemand(new ZoneDemandEvent("Z-N", "North", 42, "03:30", 500, 100, at));

        List<ZoneDistribution> result = captured;
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tick()).isEqualTo(42);
        assertThat(result.getFirst().timestamp()).isEqualTo(at);
        assertThat(result.getFirst().zoneName()).isEqualTo("North");
    }
}
