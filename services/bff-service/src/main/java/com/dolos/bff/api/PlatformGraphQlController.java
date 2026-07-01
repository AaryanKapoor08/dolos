package com.dolos.bff.api;

import com.dolos.bff.api.GraphTypes.AccountGraph;
import com.dolos.bff.api.GraphTypes.Alert;
import com.dolos.bff.api.GraphTypes.Case;
import com.dolos.bff.api.GraphTypes.CopilotAnswer;
import com.dolos.bff.api.GraphTypes.CopilotInput;
import com.dolos.bff.api.GraphTypes.Transaction;
import com.dolos.bff.service.BackendClient;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/**
 * The GraphQL entry points and field resolvers (Phase 5D). Top-level queries fetch a single entity; the
 * {@code @SchemaMapping} methods on {@code Alert} lazily compose its neighbours — so the DoD query
 * {@code alert { case { … } transaction { … } accountGraph { … } }} triggers exactly the downstream
 * calls the client selected, and nothing more. Everything returns a reactive {@code Mono}/list, which
 * Spring for GraphQL resolves against the WebFlux engine.
 */
@Controller
public class PlatformGraphQlController {

    private final BackendClient backend;

    public PlatformGraphQlController(BackendClient backend) {
        this.backend = backend;
    }

    // --- Top-level queries -----------------------------------------------------------------------

    @QueryMapping
    public Mono<List<Alert>> alertQueue(@Argument Integer size) {
        return backend.alertQueue(size == null ? 20 : size);
    }

    @QueryMapping
    public Mono<Alert> alert(@Argument String id) {
        return backend.alertById(id);
    }

    // `case` is a Java keyword, so the method is named caseById and bound to the schema field by name.
    @QueryMapping(name = "case")
    public Mono<Case> caseById(@Argument String id) {
        return backend.caseById(id);
    }

    @QueryMapping
    public Mono<Transaction> transaction(@Argument String id) {
        return backend.transactionById(id);
    }

    @QueryMapping
    public Mono<AccountGraph> accountGraph(@Argument String id) {
        return backend.accountGraph(id);
    }

    // --- Alert's composed neighbours (resolved only if selected) ---------------------------------

    @SchemaMapping(typeName = "Alert", field = "case")
    public Mono<Case> alertCase(Alert alert) {
        return backend.caseByAlertId(alert.alertId());
    }

    @SchemaMapping(typeName = "Alert", field = "transaction")
    public Mono<Transaction> alertTransaction(Alert alert) {
        if (alert.transactionId() == null) {
            return Mono.empty(); // ring alerts have no single triggering transaction
        }
        return backend.transactionById(alert.transactionId());
    }

    @SchemaMapping(typeName = "Alert", field = "accountGraph")
    public Mono<AccountGraph> alertAccountGraph(Alert alert) {
        if (alert.accountId() == null) {
            return Mono.empty();
        }
        return backend.accountGraph(alert.accountId());
    }

    // --- Mutation --------------------------------------------------------------------------------

    @MutationMapping
    public Mono<CopilotAnswer> copilot(@Argument CopilotInput input) {
        return backend.copilot(input.question());
    }
}
