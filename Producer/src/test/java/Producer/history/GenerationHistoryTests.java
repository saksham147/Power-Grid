package Producer.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.generation.GenerationService;
import Producer.kafka.ProducerOutputPublisher;
import Producer.model.GenerationRecord;
import Producer.model.GenerationRecordRepository;
import Producer.model.GenerationRollupPoint;
import Producer.model.GenerationRollupQuery;
import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import jakarta.persistence.EntityManagerFactory;

/**
 * Generation history against real Postgres (TimescaleDB, specifically -- {@code
 * GenerationHypertableSetup} runs against this test's own schema exactly like it does against the
 * real one).
 *
 * <p>
 * Real Postgres, not an embedded substitute: {@code time_bucket}, identity columns, JDBC batching,
 * transaction sharing between JPA and {@code JdbcTemplate}, and TimescaleDB's own hypertable and
 * continuous-aggregate machinery are exactly the behaviour under test, and an embedded database
 * would not reproduce any of it.
 *
 * <p>
 * Isolated in its own {@code producer_test} schema, because these tests delete rows -- run against
 * the real {@code producer} schema, they would delete real history. Kafka publishing is mocked so
 * a tick emits nothing, and both background loops are off so nothing ticks or refreshes the
 * aggregate underneath the assertions -- refreshing it is this test's own job now, done explicitly
 * with {@code CALL refresh_continuous_aggregate}, since there is no more scheduled job object to
 * call directly the way {@code GenerationRollupJob.rollUp(now)} used to be called.
 *
 * <h2>What moved out of this file</h2>
 *
 * The old suite re-verified the rollup job's own bucketing, its exact-energy-preservation, its
 * idempotent re-run, and its cutoff-truncation-to-the-minute -- all now TimescaleDB's own
 * guarantees, not this project's code to re-prove. What replaced them: {@code
 * continuousAggregateSummarisesRawRowsAfterARefresh} (the aggregate's numbers are right) and
 * {@code historyRoutesRecentRowsToRawAndOlderRowsToTheAggregate} (this project's own new logic --
 * {@link GenerationHistoryQuery} picking the right source for a given time range -- is right).
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=producer_test",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "producer.simulation.autostart=false",
        "producer.roster-sync.enabled=false"
})
class GenerationHistoryTests {

    /** Well within the 24h raw-retention window, so rows at this instant are always read as raw. */
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:30Z");
    /** Well before it -- any row at this instant only exists (once refreshed) in the aggregate. */
    private static final Instant LONG_AGO = Instant.parse("2026-09-01T09:15:00Z");

    @MockitoBean
    private ProducerOutputPublisher publisher;

    @Autowired
    private GenerationService generationService;
    @Autowired
    private GenerationHistoryQuery historyQuery;
    @Autowired
    private GenerationRollupQuery rollupQuery;
    @Autowired
    private PowerPlantRepository plants;
    @Autowired
    private GenerationRecordRepository records;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @BeforeEach
    void cleanSchema() {
        records.deleteAll();
        plants.deleteAll();
        // A continuous aggregate isn't written to directly (no deleteAll to call) -- refreshing a
        // window wide enough to cover every instant any test in this file uses recomputes it from
        // whatever the source table holds right now, which cleanSchema just made "nothing".
        refreshRollup(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }

    // ---------------------------------------------------------------- the write path

    @Test
    void aTickWritesOneRowPerActivePlantMatchingItsEvent() {
        PowerPlant thermal = plants.save(new PowerPlant("T", PlantType.THERMAL, 500, 200, 400));
        PowerPlant solar = plants.save(new PowerPlant("S", PlantType.SOLAR, 200, 0, 0));
        PowerPlant idle = new PowerPlant("I", PlantType.WIND, 150, 0, 0);
        idle.setActive(false);
        plants.save(idle);

        List<ProducerOutputEvent> events = generationService.handleTick(new GridTickEvent(144, -0.1));

        List<GenerationRecord> written = records.findAll();
        assertThat(written).hasSize(2);
        assertThat(written).extracting(GenerationRecord::getPlantId)
                .containsExactlyInAnyOrder(thermal.getId(), solar.getId());

        for (ProducerOutputEvent event : events) {
            GenerationRecord row = written.stream()
                    .filter(r -> r.getPlantId() == event.producerId()).findFirst().orElseThrow();
            assertThat(row.getOutputMw()).isEqualTo(event.outputMw());
            assertThat(row.getTickNumber()).isEqualTo(144);
            assertThat(row.getFrequencyDeviation()).isEqualTo(-0.1);
            // Postgres stores microseconds; the event carries the JVM's full precision.
            assertThat(Duration.between(row.getRecordedAt(), event.timestamp()).abs())
                    .isLessThan(Duration.ofMillis(1));
        }
    }

    /**
     * History is inserted by one JDBC batch, not by Hibernate, so a tick's Hibernate statement
     * count must not grow with the fleet. Were the inserts going through JPA with an identity id
     * they would be one statement per plant; here a ten-fold larger fleet costs the same.
     */
    @Test
    void aTicksWriteCostDoesNotGrowWithTheFleet() {
        long smallFleet = hibernateStatementsForATickOf(3);
        long largeFleet = hibernateStatementsForATickOf(30);

        System.out.printf("[batching] Hibernate statements per tick: 3 plants -> %d, 30 plants -> %d%n",
                smallFleet, largeFleet);

        assertThat(largeFleet).isEqualTo(smallFleet);
        assertThat(records.count()).isEqualTo(30);
    }

    /**
     * The claim the JDBC writer depends on: it joins the tick's JPA transaction rather than
     * committing on its own. A tick that fails after its history insert must leave neither the
     * history rows nor the plant's updated energy behind -- otherwise history and current state
     * could disagree.
     */
    @Test
    void aFailedTickRollsBackItsHistoryAndItsWriteBackTogether() {
        PowerPlant plant = plants.save(new PowerPlant("T", PlantType.THERMAL, 500, 200, 400));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("broker down"))
                .given(publisher).publish(org.mockito.ArgumentMatchers.any());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> generationService.handleTick(new GridTickEvent(1, 0.0)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(records.count()).as("history rows after a rolled-back tick").isZero();
        assertThat(plants.findById(plant.getId()).orElseThrow().getEnergyMwh())
                .as("plant energy after a rolled-back tick").isZero();
    }

    private long hibernateStatementsForATickOf(int plantCount) {
        records.deleteAll();
        plants.deleteAll();
        List<PowerPlant> fleet = new ArrayList<>();
        for (int i = 0; i < plantCount; i++) {
            fleet.add(new PowerPlant("T" + i, PlantType.THERMAL, 500, 200, 400));
        }
        plants.saveAll(fleet);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        generationService.handleTick(new GridTickEvent(1, 0.0));
        return stats.getPrepareStatementCount();
    }

    // ---------------------------------------------------------------- the continuous aggregate

    /** Energy is additive, so the aggregate must preserve it exactly -- the claim that matters
     *  most, same as it did for the old rollup job's own version of this test. */
    @Test
    void continuousAggregateSummarisesRawRowsAfterARefresh() {
        long plantId = 7;
        Instant minute = LONG_AGO;
        double[] outputs = rampFrom(100);
        records.saveAll(minuteOfTicks(plantId, minute, 1, outputs));

        refreshRollup(minute.minusSeconds(60), minute.plusSeconds(120));

        List<GenerationRollupPoint> buckets = rollupQuery.findHistory(
                plantId, minute.minusSeconds(60), minute.plusSeconds(120), 10);
        assertThat(buckets).hasSize(1);
        assertBucket(buckets.getFirst(), minute, outputs, 1, 12);
    }

    @Test
    void aSecondRefreshOfTheSameWindowChangesNothing() {
        long plantId = 3;
        records.saveAll(minuteOfTicks(plantId, LONG_AGO, 1, rampFrom(50)));
        refreshRollup(LONG_AGO.minusSeconds(60), LONG_AGO.plusSeconds(120));

        refreshRollup(LONG_AGO.minusSeconds(60), LONG_AGO.plusSeconds(120));

        assertThat(rollupQuery.findHistory(plantId, LONG_AGO.minusSeconds(60), LONG_AGO.plusSeconds(120), 10))
                .hasSize(1);
    }

    // ---------------------------------------------------------------- lifecycle and reads

    @Test
    void deletingAPlantKeepsItsHistory() {
        PowerPlant plant = plants.save(new PowerPlant("Doomed", PlantType.THERMAL, 500, 200, 400));
        generationService.handleTick(new GridTickEvent(1, 0.0));
        long id = plant.getId();

        plants.deleteById(id);

        assertThat(plants.existsById(id)).isFalse();
        assertThat(records.findAll()).extracting(GenerationRecord::getPlantId).containsExactly(id);
    }

    /**
     * The new split this class's own doc explains: a range spanning both the raw-retention window
     * and further back must come back as raw points for the recent slice and aggregate points for
     * the older one, with nothing counted twice at the boundary.
     */
    @Test
    void historyRoutesRecentRowsToRawAndOlderRowsToTheAggregate() {
        long plantId = 42;
        records.saveAll(minuteOfTicks(plantId, LONG_AGO, 1, rampFrom(60)));
        refreshRollup(LONG_AGO.minusSeconds(60), LONG_AGO.plusSeconds(120));
        records.saveAll(minuteOfTicks(plantId, NOW.minus(Duration.ofMinutes(5)), 900, rampFrom(300)));

        List<HistoryPoint> points = historyQuery.history(plantId,
                LONG_AGO.minusSeconds(60), NOW, 100);

        assertThat(points).hasSize(13);
        assertThat(points).isSortedAccordingTo(Comparator.comparing(HistoryPoint::at).reversed());
        assertThat(points.subList(0, 12)).allMatch(p -> p.resolution() == HistoryPoint.Resolution.RAW);

        HistoryPoint rolled = points.getLast();
        assertThat(rolled.resolution()).isEqualTo(HistoryPoint.Resolution.ROLLUP);
        assertThat(rolled.samples()).isEqualTo(12);
    }

    @Test
    void historyHonoursTheLimit() {
        records.saveAll(minuteOfTicks(9, NOW.minus(Duration.ofMinutes(5)), 1, rampFrom(10)));

        assertThat(historyQuery.history(9, NOW.minus(Duration.ofDays(1)), NOW, 5)).hasSize(5);
    }

    // ---------------------------------------------------------------- helpers

    /** Stands in for the old {@code GenerationRollupJob.rollUp(now)} call: recomputes the
     *  aggregate for exactly the given window from whatever {@code generation_record} currently
     *  holds. Real TimescaleDB deployments do this on a background policy; a test wants it to
     *  happen synchronously and only for the window it just wrote. */
    private void refreshRollup(Instant from, Instant to) {
        jdbc.execute("call refresh_continuous_aggregate('" + schema + ".generation_rollup', '"
                + from.atOffset(ZoneOffset.UTC) + "', '" + to.atOffset(ZoneOffset.UTC) + "')");
    }

    /** Twelve ticks, five real seconds apart -- one real minute, one simulated hour. */
    private static List<GenerationRecord> minuteOfTicks(long plantId, Instant minuteStart, long firstTick,
            double[] outputs) {
        List<GenerationRecord> rows = new ArrayList<>();
        for (int i = 0; i < outputs.length; i++) {
            rows.add(new GenerationRecord(plantId, PlantType.THERMAL, firstTick + i, outputs[i], 0.0,
                    minuteStart.plusSeconds(5L * i)));
        }
        return rows;
    }

    private static double[] rampFrom(double start) {
        double[] outputs = new double[12];
        for (int i = 0; i < 12; i++) {
            outputs[i] = start + i * 2.5;
        }
        return outputs;
    }

    private static void assertBucket(GenerationRollupPoint bucket, Instant start, double[] outputs,
            long firstTick, long lastTick) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double mw : outputs) {
            sum += mw;
            min = Math.min(min, mw);
            max = Math.max(max, mw);
        }

        assertThat(bucket.bucketStart()).isEqualTo(start);
        assertThat(bucket.sampleCount()).isEqualTo(12);
        assertThat(bucket.avgOutputMw()).isCloseTo(sum / outputs.length, within(1e-9));
        assertThat(bucket.minOutputMw()).isEqualTo(min);
        assertThat(bucket.maxOutputMw()).isEqualTo(max);
        assertThat(bucket.energyMwh()).isCloseTo(sum * 5 / 60.0, within(1e-9));
        assertThat(bucket.firstTick()).isEqualTo(firstTick);
        assertThat(bucket.lastTick()).isEqualTo(lastTick);
    }
}
