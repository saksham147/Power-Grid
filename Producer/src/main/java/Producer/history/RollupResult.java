package Producer.history;

import java.time.Instant;

/**
 * What one rollup run did.
 *
 * @param cutoff      rows recorded before this instant were rolled up; always on
 *                    a whole minute
 * @param bucketsWritten rollup rows inserted
 * @param rawDeleted  raw rows removed -- normally {@code 12 x bucketsWritten}
 */
public record RollupResult(Instant cutoff, int bucketsWritten, int rawDeleted) {
}
