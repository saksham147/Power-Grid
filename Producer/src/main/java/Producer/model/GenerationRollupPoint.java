package Producer.model;

import java.time.Instant;

/**
 * One plant's generation over one real minute -- which is exactly one simulated hour. Read-only:
 * unlike {@link GenerationRecord}, this is not a JPA entity, because there is no longer a table
 * behind it for Hibernate to manage. It reads {@code producer.generation_rollup}, which is a
 * TimescaleDB continuous aggregate now -- a view, materialized and refreshed by the database
 * itself, not a table any {@code ddl-auto} setting should ever try to alter. See {@link
 * GenerationHypertableSetup} for how it is built, and {@link GenerationRollupQuery} for how it is
 * read (a plain {@code JdbcTemplate}, the only thing that still fits once there is no entity).
 *
 * <h2>Why a minute</h2>
 *
 * The simulation runs 60x faster than real time. A real minute is 12 ticks, one simulated hour, so
 * a day of rollups still carries 24 points per simulated day and the solar curve survives.
 *
 * <h2>What is exact and what is not</h2>
 *
 * {@code energyMwh} is exact: energy is additive, so the bucket total equals the raw rows it
 * summarises. {@code minOutputMw} and {@code maxOutputMw} keep the extremes. Only the shape
 * <em>within</em> the simulated hour is given up. {@code sampleCount} is normally 12; fewer means
 * a restart or an outage left a gap in that minute.
 */
public record GenerationRollupPoint(
        long plantId,
        PlantType plantType,
        Instant bucketStart,
        double avgOutputMw,
        double minOutputMw,
        double maxOutputMw,
        double energyMwh,
        int sampleCount,
        long firstTick,
        long lastTick) {
}
