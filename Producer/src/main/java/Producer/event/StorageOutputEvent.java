package Producer.event;

import java.time.Instant;

/**
 * One storage unit's net contribution for one tick, published to {@code producer.storage-output}
 * -- kept as its own event/topic rather than folded into {@link ProducerOutputEvent}/{@code
 * producer.output} so that event's schema stays undisturbed for every consumer already reading it.
 *
 * @param netKw signed: positive while discharging (acts like generation), negative while charging
 *              (acts like extra demand), zero while idle
 */
public record StorageOutputEvent(Long unitId, long tickNumber, double netKw, Instant timestamp) {
}
