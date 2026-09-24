package Grid.model;

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
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;

/** The idempotent skip, the constraint-and-hypertable sequence when it isn't one yet, and the
 *  "never let this break the boot" contract every startup-time DB fixup in this project shares
 *  ({@code Billing.model.TransactionTypeConstraintSync} is the sibling this mirrors). */
class GridHypertableSetupTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);

    private GridHypertableSetup setup(String schema) {
        return new GridHypertableSetup(jdbc, emf, schema);
    }

    @Test
    void doesNothingWhenAlreadyAHypertable() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willReturn(true);

        setup("grid").ensureHypertable();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void convertsItWhenItIsNotAHypertableYet() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willReturn(false);

        setup("grid").ensureHypertable();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(6)).execute(sql.capture());
        var statements = sql.getAllValues();
        // Constraints dropped and recreated as composite (including recorded_at) before the
        // TimescaleDB call, which would otherwise refuse the table -- see the class's own doc.
        assertThat(statements.get(0)).contains("drop constraint if exists tick_record_pkey");
        assertThat(statements.get(2)).contains("primary key (id, recorded_at)");
        assertThat(statements.get(3)).contains("unique (tick_number, recorded_at)");
        assertThat(statements.get(4)).contains("create_hypertable('grid.tick_record'");
        assertThat(statements.get(5)).contains("add_retention_policy('grid.tick_record'");
    }

    @Test
    void usesTheConfiguredSchemaInEveryStatement() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willReturn(false);

        setup("custom_schema").ensureHypertable();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(6)).execute(sql.capture());
        assertThatCode(() -> sql.getAllValues().forEach(s ->
                assertThat(s).contains("custom_schema.tick_record")))
                .doesNotThrowAnyException();
    }

    @Test
    void aFailureIsSwallowedNotPropagated() {
        given(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .willThrow(new RuntimeException("db is down"));

        assertThatCode(() -> setup("grid").ensureHypertable()).doesNotThrowAnyException();
    }
}
