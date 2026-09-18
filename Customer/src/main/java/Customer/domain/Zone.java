package Customer.domain;

/**
 * A named grouping of {@link ConsumerUnit}s -- a neighbourhood, a district, whatever the
 * installation wants to call a cluster of houses, factories and commercial buildings that share a
 * distribution point.
 *
 * <p>
 * A zone carries no demand of its own: it is a pure container. Its demand is the sum of its
 * units' -- see {@link DemandModel} -- so a zone with no units yet reports zero, not an error.
 */
public record Zone(String zoneId, String name) {

    public Zone {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("zone " + zoneId + " must have a name");
        }
    }
}
