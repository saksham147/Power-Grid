package Billing.history;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingHistoryProperties.class)
public class HistoryConfig {
}
