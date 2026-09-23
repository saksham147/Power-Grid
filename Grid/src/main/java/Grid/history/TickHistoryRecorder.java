package Grid.history;

import java.time.Instant;

/**
 * Records one tick's system-wide figures for the history chart. A port, not a repository call
 * inline in {@link Grid.simulation.GridClockRunner}: that class calling its own method to write
 * history is exactly the self-invocation bug already found and fixed twice elsewhere in this
 * project ({@code Billing.billing.MaintenanceChargeJob}, {@code Producer.storage.
 * StorageCycleService}) -- a {@code @Transactional} annotation on a method a class calls on
 * itself never passes through Spring's transaction proxy, so the write silently never commits.
 * Injecting this as a separate bean, the same shape {@code Billing.model.BillingLedger} and
 * {@code Distributor.distribution.DistributionLogger} already use, means the call from {@code
 * GridClockRunner} crosses a real proxy boundary and is genuinely transactional.
 */
public interface TickHistoryRecorder {

    void record(long tickNumber, String simulatedTime, long simulatedDay, double frequencyDeviation,
            double totalSupplyKw, double totalDemandKw, boolean loadExceeded, Instant recordedAt);
}
