package Distributor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Distributor.distribution.DistributionService;
import Distributor.event.ProducerOutputEvent;

/**
 * Tracks Producer's fleet output as it streams in.
 *
 * <p>
 * A failure here is logged and swallowed rather than thrown: letting an exception escape a
 * {@code @KafkaListener} method retries the same record forever under the default error handling,
 * wedging this topic's partition and silently freezing this plant's contribution to every future
 * balance.
 */
@Component
public class ProducerOutputListener {

    private static final Logger log = LoggerFactory.getLogger(ProducerOutputListener.class);

    private final DistributionService distributionService;

    public ProducerOutputListener(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @KafkaListener(topics = "producer.output", containerFactory = "producerOutputListenerContainerFactory",
            autoStartup = "${distributor.autostart:true}")
    void onProducerOutput(ProducerOutputEvent event) {
        try {
            distributionService.onProducerOutput(event);
        } catch (Exception e) {
            log.error("Failed to record output for plant {} (tick {})", event.producerId(), event.tickNumber(), e);
        }
    }
}
