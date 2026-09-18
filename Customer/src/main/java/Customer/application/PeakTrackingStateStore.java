package Customer.application;

import Customer.domain.SimulationClock;
import reactor.core.publisher.Mono;

/**
 * Wraps the real {@link DemandStateStore}, updating {@link PeakTracker} from every snapshot before
 * delegating -- a decorator rather than a change to {@link DemandSimulator} itself, which is
 * deliberately framework-free and depends only on the four interfaces its own javadoc names
 * (Kafka, Redis and now peak-tracking are all infrastructure concerns it should stay ignorant of).
 * Wired in place of the real store in {@code SimulationConfig}.
 */
public class PeakTrackingStateStore implements DemandStateStore {

    private final DemandStateStore delegate;
    private final PeakTracker peaks;
    private final SimulationClock clock;

    public PeakTrackingStateStore(DemandStateStore delegate, PeakTracker peaks, SimulationClock clock) {
        this.delegate = delegate;
        this.peaks = peaks;
        this.clock = clock;
    }

    @Override
    public void save(DemandSnapshot snapshot) {
        var time = clock.timeOfDay(snapshot.tick());
        var dayNumber = clock.dayNumber(snapshot.tick());
        snapshot.zones().forEach(zone -> peaks.record(zone.zoneId(), zone.demandKw(), time, dayNumber));

        delegate.save(snapshot);
    }

    @Override
    public Mono<CurrentDemand> current() {
        return delegate.current();
    }
}
