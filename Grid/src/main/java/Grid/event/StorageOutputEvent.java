package Grid.event;

import java.time.Instant;

/**
 * A storage unit's net contribution for one tick, consumed from {@code producer.storage-output}.
 * Structural mirror of {@code Producer.event.StorageOutputEvent}, same convention as this
 * package's other mirrored events.
 *
 * @param netKw signed: positive while discharging (supply), negative while charging (demand)
 */
public record StorageOutputEvent(Long unitId, long tickNumber, double netKw, Instant timestamp) {
}
