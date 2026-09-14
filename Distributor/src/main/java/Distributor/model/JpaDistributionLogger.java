package Distributor.model;

import java.time.Instant;

import org.springframework.stereotype.Component;

import Distributor.distribution.DistributionLogger;
import Distributor.distribution.ZoneDistribution;

/** The {@link DistributionLogger} adapter: one row per balance, via plain {@code JpaRepository.save}. */
@Component
public class JpaDistributionLogger implements DistributionLogger {

    private final DistributionRecordRepository repository;

    public JpaDistributionLogger(DistributionRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public void log(ZoneDistribution distribution) {
        repository.save(new DistributionRecord(
                distribution.zoneId(), distribution.zoneName(), distribution.tick(),
                distribution.demandKw(), distribution.suppliedKw(), distribution.balanceKw(), Instant.now()));
    }
}
