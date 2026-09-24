package Producer.history;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import Producer.model.GenerationHypertableSetup;
import Producer.model.GenerationRecordRepository;
import Producer.model.GenerationRollupQuery;

/**
 * Reads a plant's history across both tables as one newest-first series.
 *
 * <h2>Why a boundary, not a merge</h2>
 *
 * This used to query both tables over the <em>same</em> range and rely on {@code REPEATABLE_READ}
 * to avoid double-counting a minute the rollup job happened to be moving between them mid-query
 * -- rows genuinely lived in exactly one table or the other, and which one could change under a
 * concurrent transaction. Neither is true anymore: {@code generation_record} (raw) and {@code
 * generation_rollup} (the continuous aggregate) are two independent, non-destructive views of
 * overlapping history now, refreshed and pruned on their own separate schedules with no
 * transactional relationship between them. Querying both over the same range would double-count
 * whatever is both still raw <em>and</em> already materialized.
 *
 * <p>
 * So each request range is split once, at {@link GenerationHypertableSetup#RAW_RETENTION} back
 * from {@code to} -- raw for the slice at or after the cutoff, the aggregate for the slice before
 * it -- matching the retention policy's own boundary exactly, rather than relying on the two
 * sources happening not to overlap. Measured from {@code to}, not {@code Instant.now()}: {@code
 * to} is "now" for every real caller ({@link Producer.api.GenerationHistoryController} defaults it
 * to exactly that), but pinning the cutoff to the wall clock instead of the query's own inputs
 * would make this method's answer depend on when it happens to run rather than what it was asked,
 * and untestable with a fixed reference instant the way {@code rollUp(Instant now)} used to be.
 *
 * <p>
 * The cutoff is also truncated to the minute, matching the aggregate's own per-minute buckets --
 * without that, a minute whose bucket straddled the untruncated cutoff would be counted whole by
 * the aggregate side while its later seconds were also counted by the raw side. The old rollup job
 * truncated its own cutoff the same way, for the same reason (see {@code Billing.billing.
 * UnlockService}'s own doc for the day-granularity version of this same bug, caught and measured
 * there at roughly an 11% over-count before the fix).
 */
@Component
public class GenerationHistoryQuery {

    private final GenerationRecordRepository records;
    private final GenerationRollupQuery rollups;

    public GenerationHistoryQuery(GenerationRecordRepository records, GenerationRollupQuery rollups) {
        this.records = records;
        this.rollups = rollups;
    }

    /** Each source is limited to {@code limit} before the merge; the newest {@code limit} of the
     *  combined series is always within those. */
    public List<HistoryPoint> history(long plantId, Instant from, Instant to, int limit) {
        Instant cutoff = to.minus(GenerationHypertableSetup.RAW_RETENTION).truncatedTo(ChronoUnit.MINUTES);
        Instant rawFrom = maxOf(from, cutoff);
        Instant rollupTo = minOf(to, cutoff);

        Stream<HistoryPoint> raw = rawFrom.isBefore(to)
                ? records.findHistory(plantId, rawFrom, to, Limit.of(limit)).stream().map(HistoryPoint::of)
                : Stream.empty();
        Stream<HistoryPoint> rolled = from.isBefore(rollupTo)
                ? rollups.findHistory(plantId, from, rollupTo, limit).stream().map(HistoryPoint::of)
                : Stream.empty();

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
     * already written here.
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

    private static Instant maxOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant minOf(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
