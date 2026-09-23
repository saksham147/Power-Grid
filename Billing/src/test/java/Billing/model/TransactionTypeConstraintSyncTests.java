package Billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * The constraint the database keeps on {@code wallet_transaction.type} is compared against the
 * enum: an old one that predates a value is detected, a current one is left alone, and the
 * replacement lists every value.
 */
class TransactionTypeConstraintSyncTests {

    /** What Postgres reports for the constraint as it was before {@code BILL_REVENUE} existed. */
    private static final String OLD_DEFINITION = "CHECK (((type)::text = ANY ((ARRAY['BILL_DEBIT'::character varying, "
            + "'PLANT_PURCHASE'::character varying, 'PLANT_UPGRADE'::character varying, "
            + "'PLANT_MAINTENANCE'::character varying, 'PLANT_DECOMMISSION'::character varying, "
            + "'STORAGE_PURCHASE'::character varying])::text[])))";

    @Test
    void anOldConstraintIsMissingTheNewestValue() {
        assertThat(TransactionTypeConstraintSync.missingValues(OLD_DEFINITION)).containsExactly("BILL_REVENUE");
    }

    @Test
    void aConstraintThatListsEveryValueIsLeftAlone() {
        String current = "CHECK (type IN (" + String.join(", ", Arrays.stream(TransactionType.values())
                .map(t -> "'" + t.name() + "'::character varying").toList()) + "))";

        assertThat(TransactionTypeConstraintSync.missingValues(current)).isEmpty();
    }

    @Test
    void theReplacementConstraintAllowsEveryEnumValue() {
        String clause = TransactionTypeConstraintSync.checkClause();

        assertThat(clause).startsWith("check (type in (").endsWith("))");
        for (TransactionType type : TransactionType.values()) {
            assertThat(clause).contains("'" + type.name() + "'");
        }
        // Re-creating from the enum yields a constraint that then needs no further change.
        assertThat(TransactionTypeConstraintSync.missingValues(clause)).isEmpty();
    }
}
