package Grid.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Grid.event.ZoneBalanceEvent;
import Grid.simulation.GridStateTracker;

/**
 * Tracks each zone's demand as Distributor publishes it, so {@link Grid.simulation.GridClockRunner}
 * has a current system-wide demand figure to weigh against supply on every tick.
 *
 * <p>
 * Reads {@code demandKw} off the balance event rather than {@code customer.demand} directly: Grid
 * is specified to consume from Producer and Distributor, not reach past Distributor to Customer.
 *
 * <p>
 * A failure here is logged and swallowed rather than thrown, for the same reason as
 * {@link ProducerOutputListener}: an escaping exception would wedge this topic's partition and
 * stop every zone after it from ever informing automatic control again.
 */
@Component
public class ZoneBalanceListener {

    private static final Logger log = LoggerFactory.getLogger(ZoneBalanceListener.class);

    private final GridStateTracker state;

    public ZoneBalanceListener(GridStateTracker state) {
        this.state = state;
    }

    @KafkaListener(topics = "distributor.zone-balance", containerFactory = "zoneBalanceListenerContainerFactory",
            autoStartup = "${grid.simulation.autostart:true}")
    void onZoneBalance(ZoneBalanceEvent event) {
        try {
            state.recordDemand(event.zoneId(), event.demandKw());
        } catch (Exception e) {
            log.error("Failed to record demand for zone {} (tick {})", event.zoneId(), event.tick(), e);
        }
    }
}
