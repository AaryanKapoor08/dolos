package com.dolos.bff;

import static org.mockito.Mockito.when;

import com.dolos.bff.api.GraphTypes.AccountGraph;
import com.dolos.bff.api.GraphTypes.Alert;
import com.dolos.bff.api.GraphTypes.Case;
import com.dolos.bff.api.GraphTypes.Transaction;
import com.dolos.bff.api.PlatformGraphQlController;
import com.dolos.bff.service.BackendClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

/**
 * DoD for Phase 5D: a single GraphQL query returns an alert together with its case, its triggering
 * transaction, and its account-graph neighbourhood. This slice test wires the GraphQL controller +
 * schema and mocks {@link BackendClient} (the only downstream seam), proving the schema, the field
 * resolvers, and the {@code @SchemaMapping} composition are correctly wired without standing up the
 * backend services.
 */
@GraphQlTest(PlatformGraphQlController.class)
class PlatformGraphQlControllerTest {

    @Autowired GraphQlTester graphQlTester;

    @MockitoBean BackendClient backend;

    @Test
    void alertComposesCaseTransactionAndGraphInOneQuery() {
        Alert alert =
                new Alert(
                        "a1", "TRANSACTION", "HIGH", "Big wire", "t1", "acc-1", 88,
                        List.of("LARGE_AMOUNT"), "detail", "2026-07-01T00:00:00Z");
        when(backend.alertById("a1")).thenReturn(Mono.just(alert));
        when(backend.caseByAlertId("a1"))
                .thenReturn(
                        Mono.just(
                                new Case(
                                        "c1", "OPEN", "a1", "acc-1", 88, null, "system", null, null,
                                        null, null, List.of())));
        when(backend.transactionById("t1"))
                .thenReturn(
                        Mono.just(
                                new Transaction(
                                        "t1", "acc-1", "acc-2", "9000", "USD", "DEBIT", "wire",
                                        "2026-07-01T00:00:00Z")));
        when(backend.accountGraph("acc-1"))
                .thenReturn(
                        Mono.just(
                                new AccountGraph(
                                        "acc-1", true, List.of("ring-1"), List.of(), List.of(),
                                        List.of(), List.of())));

        graphQlTester
                .document(
                        """
                        query {
                          alert(id: "a1") {
                            alertId
                            accountId
                            case { caseId status }
                            transaction { id amount currency }
                            accountGraph { accountId inRing }
                          }
                        }
                        """)
                .execute()
                .path("alert.alertId").entity(String.class).isEqualTo("a1")
                .path("alert.case.caseId").entity(String.class).isEqualTo("c1")
                .path("alert.transaction.id").entity(String.class).isEqualTo("t1")
                .path("alert.transaction.currency").entity(String.class).isEqualTo("USD")
                .path("alert.accountGraph.inRing").entity(Boolean.class).isEqualTo(true);
    }
}
