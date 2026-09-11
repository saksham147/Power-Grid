package Producer.api.dto;

/**
 * Body of a manual tick request. Both fields are optional, so an empty POST
 * ticks with the
 * configured defaults.
 *
 * @param tickNumber         tick number to issue, or null to take the next one
 *                           from the shared clock.
 *                           Supplying one replays a specific moment: the
 *                           generation strategies
 *                           derive their noise from the tick number, so the same
 *                           number and
 *                           deviation produce the same output every time.
 * @param frequencyDeviation departure from nominal grid frequency in Hz, or null
 *                           for the configured
 *                           default. Negative means the grid is running slow,
 *                           which drives thermal
 *                           plants to generate above their setpoint.
 */
public record TickRequest(Long tickNumber, Double frequencyDeviation) {
}
