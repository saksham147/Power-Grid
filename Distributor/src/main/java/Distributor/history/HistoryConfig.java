package Distributor.history;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DistributionHistoryProperties.class)
public class HistoryConfig {
}
