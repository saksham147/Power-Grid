package Distributor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Distributor.distribution.DistributionService;
import Distributor.event.ZoneDemandEvent;

/**
 * Reacts to each zone's demand by merging it against the tracked fleet supply -- see
 * {@link DistributionService} for why this, rather than {@code grid.tick}, is what drives the
 * merge.
 *
 * <p>
 * A failure here is logged and swallowed rather than thrown, for the same reason as
 * {@link ProducerOutputListener}: an escaping exception would wedge this topic's partition and
 * stop every zone after it from ever being balanced again.
 */
@Component
public class ZoneDemandListener {

    private static final Logger log = LoggerFactory.getLogger(ZoneDemandListener.class);

    private final DistributionService distributionService;

    public ZoneDemandListener(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @KafkaListener(topics = "customer.demand", containerFactory = "zoneDemandListenerContainerFactory",
            autoStartup = "${distributor.autostart:true}")
    void onZoneDemand(ZoneDemandEvent event) {
        try {
            distributionService.onZoneDemand(event);
        } catch (Exception e) {
            log.error("Failed to balance zone {} (tick {})", event.zoneId(), event.tick(), e);
        }
    }
}
