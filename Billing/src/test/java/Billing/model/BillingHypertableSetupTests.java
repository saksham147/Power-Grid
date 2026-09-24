package Billing.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;

/**
 * The one sequencing guarantee that actually mattered for this migration -- see this class's own
 * doc for why: a newly-created aggregate must be fully backfilled before any retention policy can
 * ever drop a raw row, or history with nothing left to recompute it from would be lost outright.
 */
class BillingHypertableSetupTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);

    private BillingHypertableSetup setup() {
        return new BillingHypertableSetup(jdbc, emf, "billing");
    }

    /** Every boolean existence check this class makes, all defaulted to "not done yet" unless a
     *  test overrides one -- keeps each test's arrangement focused on the one check it cares about. */
    private void givenNothingExistsYet() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).willReturn(false);
        given(jdbc.queryForObject(anyString(), eq(Boolean.class))).willReturn(false);
    }

    @Test
    void dropsTheLegacyTableOnlyWhenItIsStillAPlainTable() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("relkind = 'r'"),
                eq(Boolean.class), any())).willReturn(true);

        setup().setUp();

        verify(jdbc).execute("drop table billing.billing_daily_rollup");
    }

    @Test
    void leavesTheAggregateAloneWhenTheLegacyCheckSaysItIsNotAPlainTable() {
        givenNothingExistsYet(); // the relkind='r' check already defaults to false here

        setup().setUp();

        verify(jdbc, never()).execute("drop table billing.billing_daily_rollup");
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

        verify(jdbc).execute("alter table billing.billing_record drop constraint if exists billing_record_pkey");
        verify(jdbc).execute("alter table billing.billing_record add constraint billing_record_pkey primary key (id, recorded_at)");
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("create_hypertable('billing.billing_record'"));
    }

    @Test
    void aNewAggregateIsBackfilledBeforeTheRetentionPolicyIsAdded() {
        givenNothingExistsYet();

        setup().setUp();

        InOrder order = org.mockito.Mockito.inOrder(jdbc);
        order.verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("create materialized view billing.billing_daily_rollup"));
        order.verify(jdbc).execute("call refresh_continuous_aggregate('billing.billing_daily_rollup', null, null)");
        order.verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("add_retention_policy('billing.billing_record'"));
    }

    @Test
    void anAggregateThatAlreadyExistsIsNeverBackfilledAgain() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("continuous_aggregates"),
                eq(Boolean.class), any())).willReturn(true);

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("create materialized view"));
        verify(jdbc, never()).execute("call refresh_continuous_aggregate('billing.billing_daily_rollup', null, null)");
        // Retention is still checked and added independently of the aggregate's own state.
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("add_retention_policy"));
    }

    @Test
    void policiesAlreadyPresentAreNeverAddedTwice() {
        givenNothingExistsYet();
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("policy_retention"), eq(Boolean.class)))
                .willReturn(true);
        given(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("policy_refresh_continuous_aggregate"), eq(Boolean.class)))
                .willReturn(true);

        setup().setUp();

        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("add_retention_policy"));
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("add_continuous_aggregate_policy"));
    }

    @Test
    void aFailureAnywhereIsSwallowedNotPropagated() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willThrow(new RuntimeException("db is down"));

        assertThatCode(() -> setup().setUp()).doesNotThrowAnyException();
    }

    @Test
    void everyStatementUsesTheConfiguredSchema() {
        var customSchemaSetup = new BillingHypertableSetup(jdbc, emf, "custom_schema");
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).willReturn(false);
        given(jdbc.queryForObject(anyString(), eq(Boolean.class))).willReturn(false);

        customSchemaSetup.setUp();

        verify(jdbc, times(0)).execute(org.mockito.ArgumentMatchers.contains("billing.billing_record"));
        assertThat(BillingHypertableSetup.RAW_RETENTION.toHours()).isEqualTo(24);
    }
}
