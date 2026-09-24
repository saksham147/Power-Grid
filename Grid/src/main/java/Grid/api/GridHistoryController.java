package Grid.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import Grid.api.dto.TickHistoryPointResponse;
import Grid.model.TickRecord;
import Grid.model.TickRecordRepository;
import Grid.simulation.SimulationClock;

/**
 * Read-only: the tick-by-tick history {@code grid.tick_record} keeps a rolling window of --
 * TimescaleDB's own retention policy prunes it now, not application code (see the migration note
 * on the {@code postgres} service in {@code docker-compose.yml}). Its own controller rather than a
 * method on {@link GridController}, which is the live clock -- this is a query over what it
 * already recorded, the same split {@code Billing.api.MoneyFlowController} makes from {@code
 * WalletController}.
 */
@RestController
@RequestMapping("/api/grid")
public class GridHistoryController {

    /** One simulated day at the default tick pace -- long enough to see a full solar/demand cycle
     *  on the chart, short enough that even the default request stays cheap. */
    private static final int DEFAULT_LIMIT = (int) SimulationClock.TICKS_PER_DAY;

    private final TickRecordRepository repository;

    public GridHistoryController(TickRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * @param limit how many of the most recent ticks to return
     * @return oldest first -- the order a line chart draws in, the opposite of how {@link
     *         TickRecordRepository#findRecent} itself reads them out
     */
    @GetMapping("/history")
    public List<TickHistoryPointResponse> history(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        List<TickRecord> recent = repository.findRecent(Limit.of(limit));
        List<TickHistoryPointResponse> points = recent.stream().map(TickHistoryPointResponse::from).toList();
        List<TickHistoryPointResponse> oldestFirst = new ArrayList<>(points);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }
}
