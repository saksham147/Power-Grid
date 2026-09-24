package Distributor.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Raw per-tick writes only -- pruning is now {@code distributor.distribution_record}'s TimescaleDB
 * retention policy, not application code. See {@link DistributionHypertableSetup}.
 */
public interface DistributionRecordRepository extends JpaRepository<DistributionRecord, Long> {
}
