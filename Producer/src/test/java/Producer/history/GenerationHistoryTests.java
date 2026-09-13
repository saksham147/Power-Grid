package Producer.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.generation.GenerationService;
import Producer.kafka.ProducerOutputPublisher;
import Producer.model.GenerationRecord;
import Producer.model.GenerationRecordRepository;
import Producer.model.GenerationRollup;
import Producer.model.GenerationRollupRepository;
import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import jakarta.persistence.EntityManagerFactory;

/**
 * Generation history against real Postgres.
 *
 * <p>
 * Real Postgres, not an embedded substitute: {@code date_trunc}, identity columns,
 * JDBC batching and
 * transaction sharing between JPA and {@code JdbcTemplate} are exactly the
 * behaviour under test, and
 * an embedded database would not reproduce them.
 *
 * <p>
 * Isolated in its own {@code producer_test} schema, because these tests delete
 * rows -- run against
 * the real {@code producer} schema, the rollup test would compress and delete real
 * history. Kafka
 * publishing is mocked so a tick emits nothing, and both background loops are off
 * so nothing ticks
 * or rolls up underneath the assertions.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=producer_test",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "producer.simulation.autostart=false",
        "producer.history.rollup-enabled=false"
})
class GenerationHistoryTests {

    /** Mid-minute on purpose: the default 24 h retention puts the cutoff at 12:00:30 yesterday. */
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:30Z");

    @MockitoBean
    private ProducerOutputPublisher publisher;

    @Autowired
    private GenerationService generationService;
    @Autowired
    private GenerationRollupJob rollupJob;
    @Autowired
    private GenerationHistoryQuery historyQuery;
    @Autowired
    private PowerPlantRepository plants;
    @Autowired
    private GenerationRecordRepository records;
    @Autowired
    private GenerationRollupRepository rollups;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanSchema() {
        rollups.deleteAll();
        records.deleteAll();
        plants.deleteAll();
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
     * History is inserted by one JDBC batch, not by Hibernate, so a tick's Hibernate
     * statement count
     * must not grow with the fleet. Were the inserts going through JPA with an
     * identity id they
     * would be one statement per plant; here a ten-fold larger fleet costs the same.
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
     * The claim the JDBC writer depends on: it joins the tick's JPA transaction rather
     * than committing
     * on its own. A tick that fails after its history insert must leave neither the
     * history rows nor
     * the plant's updated energy behind -- otherwise history and current state could
     * disagree.
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
        rollups.deleteAll();
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

    // ---------------------------------------------------------------- the rollup

    @Test
    void rollupSummarisesOldMinutesExactlyAndLeavesRecentRowsRaw() {
        long plantId = 7;
        Instant oldMinuteA = Instant.parse("2026-09-11T09:15:00Z");
        Instant oldMinuteB = Instant.parse("2026-09-11T09:16:00Z");
        double[] outputsA = rampFrom(100);
        double[] outputsB = rampFrom(250);

        records.saveAll(minuteOfTicks(plantId, oldMinuteA, 1, outputsA));
        records.saveAll(minuteOfTicks(plantId, oldMinuteB, 13, outputsB));
        List<GenerationRecord> recent = minuteOfTicks(plantId, NOW.minus(Duration.ofHours(1)), 500, rampFrom(400));
        records.saveAll(recent);

        RollupResult result = rollupJob.rollUp(NOW);

        assertThat(result.bucketsWritten()).isEqualTo(2);
        assertThat(result.rawDeleted()).isEqualTo(24);

        List<GenerationRollup> buckets = rollups.findAll().stream()
                .sorted(Comparator.comparing(GenerationRollup::getBucketStart)).toList();
        assertBucket(buckets.get(0), oldMinuteA, outputsA, 1, 12);
        assertBucket(buckets.get(1), oldMinuteB, outputsB, 13, 24);

        // Nothing inside the retention window moved.
        assertThat(records.count()).isEqualTo(recent.size());
    }

    /** Energy is additive, so compression must preserve it -- this is the claim that matters most. */
    @Test
    void rollupPreservesTotalEnergyExactly() {
        Instant minute = Instant.parse("2026-09-10T03:42:00Z");
        double[] outputs = { 0.0, 12.5, 88.25, 143.0, 199.75, 201.125, 176.0, 90.5, 44.0, 7.25, 0.0, 0.0 };
        records.saveAll(minuteOfTicks(3, minute, 1, outputs));

        double rawEnergy = 0;
        for (double mw : outputs) {
            rawEnergy += mw * 5 / 60.0;
        }

        rollupJob.rollUp(NOW);

        assertThat(rollups.findAll().getFirst().getEnergyMwh()).isCloseTo(rawEnergy, within(1e-9));
    }

    @Test
    void runningTheRollupTwiceChangesNothingTheSecondTime() {
        records.saveAll(minuteOfTicks(1, Instant.parse("2026-09-11T01:00:00Z"), 1, rampFrom(50)));

        rollupJob.rollUp(NOW);
        long bucketsAfterFirst = rollups.count();

        RollupResult second = rollupJob.rollUp(NOW);

        assertThat(second.bucketsWritten()).isZero();
        assertThat(second.rawDeleted()).isZero();
        assertThat(rollups.count()).isEqualTo(bucketsAfterFirst);
    }

    /**
     * NOW - 24h is 12:00:30. Without truncation, the rows at 12:00:10 and 12:00:20
     * would be rolled
     * now and the rest of that minute later, writing two rows for one minute. The
     * cutoff truncates
     * to 12:00:00, so the whole minute stays raw until it is complete.
     */
    @Test
    void aCutoffMidMinuteLeavesThatWholeMinuteRaw() {
        Instant cutoffMinute = Instant.parse("2026-09-12T12:00:00Z");
        Instant priorMinute = Instant.parse("2026-09-12T11:59:00Z");

        records.saveAll(List.of(
                new GenerationRecord(1, PlantType.THERMAL, 1, 100, 0, priorMinute.plusSeconds(10)),
                new GenerationRecord(1, PlantType.THERMAL, 2, 100, 0, cutoffMinute.plusSeconds(10)),
                new GenerationRecord(1, PlantType.THERMAL, 3, 100, 0, cutoffMinute.plusSeconds(20))));

        RollupResult result = rollupJob.rollUp(NOW);

        assertThat(result.cutoff()).isEqualTo(cutoffMinute);
        assertThat(rollups.findAll()).extracting(GenerationRollup::getBucketStart).containsExactly(priorMinute);
        assertThat(records.findAll()).extracting(GenerationRecord::getTickNumber).containsExactlyInAnyOrder(2L, 3L);
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

    @Test
    void historyMergesRawAndRolledUpPointsNewestFirst() {
        long plantId = 42;
        records.saveAll(minuteOfTicks(plantId, Instant.parse("2026-09-11T08:00:00Z"), 1, rampFrom(60)));
        rollupJob.rollUp(NOW);
        records.saveAll(minuteOfTicks(plantId, NOW.minus(Duration.ofMinutes(5)), 900, rampFrom(300)));

        List<HistoryPoint> points = historyQuery.history(plantId,
                NOW.minus(Duration.ofDays(3)), NOW, 100);

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

    private static void assertBucket(GenerationRollup bucket, Instant start, double[] outputs,
            long firstTick, long lastTick) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double mw : outputs) {
            sum += mw;
            min = Math.min(min, mw);
            max = Math.max(max, mw);
        }

        assertThat(bucket.getBucketStart()).isEqualTo(start);
        assertThat(bucket.getSampleCount()).isEqualTo(12);
        assertThat(bucket.getAvgOutputMw()).isCloseTo(sum / outputs.length, within(1e-9));
        assertThat(bucket.getMinOutputMw()).isEqualTo(min);
        assertThat(bucket.getMaxOutputMw()).isEqualTo(max);
        assertThat(bucket.getEnergyMwh()).isCloseTo(sum * 5 / 60.0, within(1e-9));
        assertThat(bucket.getFirstTick()).isEqualTo(firstTick);
        assertThat(bucket.getLastTick()).isEqualTo(lastTick);
    }
}
