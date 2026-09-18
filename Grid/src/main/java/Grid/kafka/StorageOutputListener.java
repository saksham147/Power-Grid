package Grid.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Grid.event.StorageOutputEvent;
import Grid.simulation.GridStateTracker;

/**
 * Tracks storage units' net contribution as it streams in, so {@link
 * Grid.simulation.GridClockRunner} weighs a discharging battery like extra supply and a charging
 * one like extra demand -- see {@link GridStateTracker#totalSupplyKw()}.
 *
 * <p>
 * A failure here is logged and swallowed for the same reason {@link ProducerOutputListener} does
 * this: an escaping exception wedges this topic's partition and silently freezes this unit's
 * contribution to every future automatic control decision.
 */
@Component
public class StorageOutputListener {

    private static final Logger log = LoggerFactory.getLogger(StorageOutputListener.class);

    private final GridStateTracker state;

    public StorageOutputListener(GridStateTracker state) {
        this.state = state;
    }

    @KafkaListener(topics = "producer.storage-output", containerFactory = "storageOutputListenerContainerFactory",
            autoStartup = "${grid.simulation.autostart:true}")
    void onStorageOutput(StorageOutputEvent event) {
        try {
            state.recordStorageNet(event.unitId(), event.netKw());
        } catch (Exception e) {
            log.error("Failed to record storage net for unit {} (tick {})", event.unitId(), event.tickNumber(), e);
        }
    }
}
