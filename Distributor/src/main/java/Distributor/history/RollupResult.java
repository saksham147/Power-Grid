package Distributor.history;

/**
 * @param cutoffTick   raw rows at or before this tick were rolled up and removed
 * @param bucketsWritten rollup rows inserted
 * @param rawDeleted   raw rows removed
 */
public record RollupResult(long cutoffTick, int bucketsWritten, int rawDeleted) {
}
