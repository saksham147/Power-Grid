package Distributor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Distributor.distribution.BalancePublisher;
import Distributor.distribution.ZoneDistribution;
import Distributor.event.ZoneBalanceEvent;

/**
 * The {@link BalancePublisher} adapter: publishes a zone's merged balance to
 * {@code distributor.zone-balance}.
 *
 * <p>
 * One publish per incoming demand event, not a per-tick fan-out over every zone the way Customer's
 * publisher is -- so there is no publish budget here. A single {@code send} against a dead broker
 * costs at most {@code max.block.ms} (bounded in {@code application.yml}), and that is one event,
 * not a whole tick's worth.
 */
@Component
public class ZoneBalancePublisher implements BalancePublisher {

    public static final String TOPIC = "distributor.zone-balance";

    private static final Logger log = LoggerFactory.getLogger(ZoneBalancePublisher.class);

    private final KafkaTemplate<String, ZoneBalanceEvent> kafkaTemplate;

    public ZoneBalancePublisher(KafkaTemplate<String, ZoneBalanceEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ZoneDistribution distribution) {
        ZoneBalanceEvent event = new ZoneBalanceEvent(
                distribution.zoneId(), distribution.zoneName(), distribution.tick(),
                distribution.demandKw(), distribution.suppliedKw(), distribution.balanceKw(),
                distribution.timestamp());

        // Keyed by zone id, matching customer.demand: one zone's balance history stays
        // ordered on one partition.
        kafkaTemplate.send(TOPIC, event.zoneId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish balance for zone {} (tick {})", event.zoneId(), event.tick(),
                                error);
                    }
                });
    }
}
