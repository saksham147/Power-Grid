package Producer.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import Producer.history.GenerationHistoryQuery;
import Producer.history.HistoryPoint;

/**
 * A plant's generation history: raw per-tick points for the last day, rolled-up
 * hourly points
 * beyond.
 *
 * <p>
 * A deleted plant's id still returns its history, because history outlives the
 * plant. The
 * consequence is that an id that never existed returns an empty list rather than
 * 404 -- there is
 * no table left to say whether a plant with no history ever existed.
 */
@RestController
@RequestMapping("/api/plants/{id}/history")
public class GenerationHistoryController {

    /** One simulated day of raw ticks. */
    static final int DEFAULT_LIMIT = 288;

    /** A week of raw history is ~120k rows per plant; nothing should pull that in one response. */
    static final int MAX_LIMIT = 5000;

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    private final GenerationHistoryQuery query;

    public GenerationHistoryController(GenerationHistoryQuery query) {
        this.query = query;
    }

    /**
     * @param from  inclusive start; defaults to 24 hours before {@code to}
     * @param to    exclusive end; defaults to now
     * @param limit maximum points, newest first
     * @throws IllegalArgumentException for an empty range or an out-of-range limit,
     *                                  mapped to 400
     */
    @GetMapping
    public List<HistoryPoint> history(@PathVariable long id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_WINDOW);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from (" + start + ") must be before to (" + end + ")");
        }

        return query.history(id, start, end, limit);
    }
}
