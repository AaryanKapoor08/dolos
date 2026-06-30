package com.dolos.casework.casequery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.stereotype.Service;

/**
 * The query-side API of {@code casequery} (Phase 3C): a thin facade over the Axon
 * {@link QueryGateway}. Other modules (the REST controller now; notifications in Phase 5) read case
 * state through this service rather than touching the read-model repositories directly.
 */
@Service
public class CaseQueryService {

    private final QueryGateway queryGateway;

    public CaseQueryService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /** Current state + timeline of one case, if it exists. */
    public Optional<CaseDetails> findById(UUID caseId) {
        return Optional.ofNullable(
                queryGateway.query(new FindCaseById(caseId), CaseDetails.class).join());
    }

    /** Every case, most-recently-updated first. */
    public List<CaseDetails> findAll() {
        return queryGateway
                .query(new FindAllCases(), ResponseTypes.multipleInstancesOf(CaseDetails.class))
                .join();
    }

    /**
     * A live subscription to one case: an initial {@link CaseDetails} plus a stream of updates pushed
     * every time the projection changes (consumed by the notification service in Phase 5). The caller
     * owns the returned handle and must close it.
     */
    public SubscriptionQueryResult<CaseDetails, CaseDetails> subscribe(UUID caseId) {
        return queryGateway.subscriptionQuery(
                new FindCaseById(caseId),
                ResponseTypes.instanceOf(CaseDetails.class),
                ResponseTypes.instanceOf(CaseDetails.class));
    }
}
