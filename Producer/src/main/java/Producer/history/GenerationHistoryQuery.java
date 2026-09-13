package Producer.history;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import Producer.model.GenerationRecordRepository;
import Producer.model.GenerationRollupRepository;

/**
 * Reads a plant's history across both tables as one newest-first series.
 */
@Component
public class GenerationHistoryQuery {

    private final GenerationRecordRepository records;
    private final GenerationRollupRepository rollups;

    public GenerationHistoryQuery(GenerationRecordRepository records, GenerationRollupRepository rollups) {
        this.records = records;
        this.rollups = rollups;
    }

    /**
     * {@code REPEATABLE_READ} is load-bearing. The rollup job moves rows <em>between</em> these two
     * tables. Under the default {@code READ_COMMITTED} each query takes its own snapshot, so a rollup
     * committing between them could show a minute twice -- raw points and their rollup -- or drop it
     * entirely. Repeatable read gives both queries the same snapshot, where a minute is in exactly
     * one table.
     *
     * <p>
     * Each table is limited to {@code limit} before the merge; the newest {@code limit} of the
     * combined series is always within those.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<HistoryPoint> history(long plantId, Instant from, Instant to, int limit) {
        Stream<HistoryPoint> raw = records.findHistory(plantId, from, to, Limit.of(limit)).stream()
                .map(HistoryPoint::of);
        Stream<HistoryPoint> rolled = rollups.findHistory(plantId, from, to, Limit.of(limit)).stream()
                .map(HistoryPoint::of);

        return Stream.concat(raw, rolled)
                .sorted(Comparator.comparing(HistoryPoint::at).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * The highest tick number either table has ever recorded, across every plant, or 0 if there is
     * no history yet.
     *
     * <p>
     * {@code SimulationRunner} keeps its tick counter in memory only and reads this once on boot to
     * resume it, rather than rewinding the simulated day to midnight and reissuing tick numbers
     * already written here. Not wrapped in a transaction: the two queries running a moment apart,
     * while a rollup happens to move the very row in question from one table to the other, is
     * harmless -- a moved row's {@code last_tick} carries the same value it had as {@code tick_number}
     * before the move, so neither query can see a lower answer than the truth.
     *
     * <p>
     * Best-effort, not exact: a tick over an empty fleet writes no row at all, so ticks issued while
     * every plant was inactive right up to shutdown are not recoverable and the clock resumes just
     * behind where it truly left off. Accepted, since an empty fleet has nothing at stake in the
     * tick number.
     */
    public long lastKnownTick() {
        Long raw = records.findMaxTickNumber();
        Long rolled = rollups.findMaxLastTick();
        return Math.max(raw != null ? raw : 0L, rolled != null ? rolled : 0L);
    }
}
