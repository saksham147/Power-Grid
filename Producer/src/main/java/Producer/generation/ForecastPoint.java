package Producer.generation;

/** One tick of a {@link ForecastService} forecast. */
public record ForecastPoint(long tickNumber, double outputMw) {
}
