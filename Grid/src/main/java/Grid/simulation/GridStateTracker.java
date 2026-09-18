package Grid.simulation;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Grid's own view of the system it is regulating: the latest known output of every plant (from
 * {@code producer.output}) and the latest known demand of every zone (from
 * {@code distributor.zone-balance}'s {@code demandKw}), kept in memory rather than queried back
 * from either service.
 *
 * <p>
 * This is Distributor's own {@code GridStateTracker} again, field-for-field: same reasoning about
 * {@code put} rather than accumulate (each record is an absolute snapshot, not a delta), the same
 * MW-to-kW conversion for {@link #totalSupplyKw()}, and the same known limitation that a
 * deactivated plant or a deleted zone leaves its last figure here forever, since neither upstream
 * event announces its own absence. Kept as Grid's own copy rather than shared, like every other
 * cross-service concept in this project.
 */
@Component
public class GridStateTracker {

    private static final double KW_PER_MW = 1000.0;

    private final ConcurrentHashMap<Long, Double> outputMwByPlant = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> demandKwByZone = new ConcurrentHashMap<>();
    /** Signed: positive while a unit discharges (acts like supply), negative while it charges
     *  (acts like demand) -- folded into {@link #totalSupplyKw()} as a net addition rather than
     *  tracked as separate supply/demand, since a single unit can be either from tick to tick. */
    private final ConcurrentHashMap<Long, Double> netKwByStorageUnit = new ConcurrentHashMap<>();

    public void recordSupply(long plantId, double outputMw) {
        outputMwByPlant.put(plantId, outputMw);
    }

    public void recordDemand(String zoneId, double demandKw) {
        demandKwByZone.put(zoneId, demandKw);
    }

    public void recordStorageNet(long unitId, double netKw) {
        netKwByStorageUnit.put(unitId, netKw);
    }

    /** Sum of every plant's latest known output (converted to kW) plus every storage unit's net
     *  contribution -- a discharging unit adds to this, a charging one subtracts from it. */
    public double totalSupplyKw() {
        double plantSupplyKw = outputMwByPlant.values().stream().mapToDouble(Double::doubleValue).sum() * KW_PER_MW;
        double storageNetKw = netKwByStorageUnit.values().stream().mapToDouble(Double::doubleValue).sum();
        return plantSupplyKw + storageNetKw;
    }

    /** Sum of every zone's latest known demand, already in kW. */
    public double totalDemandKw() {
        return demandKwByZone.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
