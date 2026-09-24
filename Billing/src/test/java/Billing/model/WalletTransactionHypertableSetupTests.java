package Billing.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;

/**
 * A newly-created aggregate is still backfilled before this class considers itself done -- see
 * {@code Billing.model.BillingHypertableSetupTests}, which this mirrors -- but unlike that sibling,
 * no retention policy is ever added here, which the last test below asserts directly.
 */
class WalletTransactionHypertableSetupTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);

    private WalletTransactionHypertableSetup setup() {
        return new WalletTransactionHypertableSetup(jdbc, emf, "billing");
    }

    private void givenNothingExistsYet() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).willReturn(false);
        given(jdbc.queryForObject(anyString(), eq(Boolean.class))).willReturn(false);
    }

    @Test
    void skipsHypertableConversionWhenAlreadyDone() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("hypertables"),
                eq(Boolean.class), any())).willReturn(true);

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("create_hypertable"));
    }

    @Test
    void convertsToAHypertableWhenNotOneYet() {
        givenNothingExistsYet();

        setup().setUp();

        verify(jdbc).execute("alter table billing.wallet_transaction drop constraint if exists wallet_transaction_pkey");
        verify(jdbc).execute("alter table billing.wallet_transaction add constraint wallet_transaction_pkey primary key (id, occurred_at)");
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("create_hypertable('billing.wallet_transaction'"));
    }

    @Test
    void aNewAggregateIsBackfilled() {
        givenNothingExistsYet();

        setup().setUp();

        InOrder order = org.mockito.Mockito.inOrder(jdbc);
        order.verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("create materialized view billing.wallet_daily_totals"));
        order.verify(jdbc).execute("call refresh_continuous_aggregate('billing.wallet_daily_totals', null, null)");
    }

    @Test
    void anAggregateThatAlreadyExistsIsNeverBackfilledAgain() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("continuous_aggregates"),
                eq(Boolean.class), any())).willReturn(true);

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("create materialized view"));
        verify(jdbc, never()).execute("call refresh_continuous_aggregate('billing.wallet_daily_totals', null, null)");
    }

    @Test
    void theRefreshPolicyIsNeverAddedTwice() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("policy_refresh_continuous_aggregate"), eq(Boolean.class)))
                .willReturn(true);

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("add_continuous_aggregate_policy"));
    }

    @Test
    void noRetentionPolicyIsEverAdded() {
        givenNothingExistsYet();

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("add_retention_policy"));
    }

    @Test
    void aFailureAnywhereIsSwallowedNotPropagated() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willThrow(new RuntimeException("db is down"));

        assertThatCode(() -> setup().setUp()).doesNotThrowAnyException();
    }
}
