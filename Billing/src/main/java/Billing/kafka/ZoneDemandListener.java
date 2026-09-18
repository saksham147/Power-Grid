package Billing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Billing.billing.BillingCycleService;
import Billing.event.ZoneDemandEvent;

/**
 * Reacts to each zone's tick-end demand by billing it -- see {@link BillingCycleService} for why
 * this, not a separate clock, drives billing cycles.
 *
 * <p>
 * A failure here is logged and swallowed rather than thrown: letting an exception escape a
 * {@code @KafkaListener} method retries the same record forever under the default error handling,
 * wedging this topic's partition and silently stopping every zone after it from ever being billed
 * again.
 */
@Component
public class ZoneDemandListener {

    private static final Logger log = LoggerFactory.getLogger(ZoneDemandListener.class);

    private final BillingCycleService billingCycleService;

    public ZoneDemandListener(BillingCycleService billingCycleService) {
        this.billingCycleService = billingCycleService;
    }

    @KafkaListener(topics = "customer.demand", autoStartup = "${billing.autostart:true}")
    void onZoneDemand(ZoneDemandEvent event) {
        try {
            billingCycleService.onZoneDemand(event);
        } catch (Exception e) {
            log.error("Failed to bill zone {} (tick {})", event.zoneId(), event.tick(), e);
        }
    }
}
