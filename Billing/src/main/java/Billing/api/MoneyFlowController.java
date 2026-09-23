package Billing.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Billing.api.dto.MoneyFlowResponse;
import Billing.billing.MoneyFlowService;

/**
 * Read-only: revenue per second by zone, plant running cost per second, and lifetime plant spend.
 * Its own controller rather than another method on {@link WalletController}, which is wallets and
 * ledgers -- this is a computed view over them, and keeping it separate leaves that controller's
 * constructor (and everything that builds it) alone.
 */
@RestController
@RequestMapping("/api/billing")
public class MoneyFlowController {

    private final MoneyFlowService moneyFlow;

    public MoneyFlowController(MoneyFlowService moneyFlow) {
        this.moneyFlow = moneyFlow;
    }

    @GetMapping("/flow")
    public MoneyFlowResponse flow() {
        return MoneyFlowResponse.from(moneyFlow.compute(Instant.now()));
    }
}
