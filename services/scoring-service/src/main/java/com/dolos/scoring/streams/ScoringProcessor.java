package com.dolos.scoring.streams;

import com.dolos.events.RiskScored;
import com.dolos.events.TransactionReceived;
import com.dolos.scoring.service.RiskScoringEngine;
import com.dolos.scoring.service.ScoreCache;
import com.dolos.scoring.service.ScoringFact;
import java.math.BigDecimal;
import java.time.Duration;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;

/**
 * The stateful heart of the Phase 2A topology. For each {@link TransactionReceived} (keyed by
 * account) it updates and queries three state stores to assemble a {@link ScoringFact}, scores it
 * with the transport-independent {@link RiskScoringEngine}, and forwards the enriched
 * {@link RiskScored} downstream.
 *
 * <ul>
 *   <li><b>velocity</b> — a window store of amounts; a trailing-window fetch gives count + sum.
 *   <li><b>structuring</b> — a window store of <em>sub-threshold</em> deposits only; the trailing-day
 *       fetch gives the deposit count + running sum that reveals deliberate sub-$10k layering.
 *   <li><b>last-seen</b> — a key-value store of the account's previous (country, time), for the
 *       impossible-travel comparison.
 * </ul>
 *
 * <p>Uses event time ({@link TransactionReceived#occurredAt()}) for all window maths so replays and
 * out-of-order delivery score deterministically — the property the {@code TopologyTestDriver} test
 * relies on.
 */
public class ScoringProcessor implements Processor<String, TransactionReceived, String, RiskScored> {

    static final String VELOCITY_STORE = "velocity-store";
    static final String STRUCTURING_STORE = "structuring-store";
    static final String LAST_SEEN_STORE = "last-seen-store";
    static final String PAYEE_STORE = "payee-store";

    private static final Duration VELOCITY_WINDOW = RiskScoringEngine.VELOCITY_WINDOW;
    private static final Duration STRUCTURING_WINDOW = RiskScoringEngine.STRUCTURING_WINDOW;
    private static final BigDecimal REPORTING_THRESHOLD = RiskScoringEngine.REPORTING_THRESHOLD;
    private static final String DEBIT = "DEBIT";

    private final RiskScoringEngine engine;
    private final ScoreCache scoreCache;

    private ProcessorContext<String, RiskScored> context;
    private WindowStore<String, BigDecimal> velocityStore;
    private WindowStore<String, BigDecimal> structuringStore;
    private KeyValueStore<String, LastSeen> lastSeenStore;
    private KeyValueStore<String, Boolean> payeeStore;

    public ScoringProcessor(RiskScoringEngine engine, ScoreCache scoreCache) {
        this.engine = engine;
        this.scoreCache = scoreCache;
    }

    @Override
    public void init(ProcessorContext<String, RiskScored> context) {
        this.context = context;
        this.velocityStore = context.getStateStore(VELOCITY_STORE);
        this.structuringStore = context.getStateStore(STRUCTURING_STORE);
        this.lastSeenStore = context.getStateStore(LAST_SEEN_STORE);
        this.payeeStore = context.getStateStore(PAYEE_STORE);
    }

    @Override
    public void process(Record<String, TransactionReceived> record) {
        String account = record.key();
        TransactionReceived txn = record.value();
        long ts = txn.occurredAt().toEpochMilli();

        velocityStore.put(account, txn.amount(), ts);
        Aggregate velocity = window(velocityStore, account, ts, VELOCITY_WINDOW);

        boolean subThreshold = txn.amount().compareTo(REPORTING_THRESHOLD) < 0;
        if (subThreshold) {
            structuringStore.put(account, txn.amount(), ts);
        }
        Aggregate structuring = window(structuringStore, account, ts, STRUCTURING_WINDOW);

        LastSeen prior = lastSeenStore.get(account);
        lastSeenStore.put(account, new LastSeen(txn.country(), ts));

        boolean newPayee = isFirstTimePayee(account, txn);

        ScoringFact fact =
                new ScoringFact(
                        txn.transactionId(),
                        account,
                        txn.amount(),
                        txn.country(),
                        ts,
                        txn.counterpartyAccountId(),
                        txn.direction(),
                        velocity.count,
                        velocity.sum,
                        structuring.count,
                        structuring.sum,
                        prior == null ? null : prior.country(),
                        prior == null ? null : prior.occurredAtMs(),
                        newPayee);

        RiskScored scored = engine.score(fact);
        // Cache the detail before forwarding so a synchronous GetScoreDetails callback (e.g. from
        // alert-service reacting to the RiskScored we are about to emit) finds it.
        scoreCache.put(scored);
        context.forward(record.withValue(scored));
    }

    /**
     * Whether this is the first time {@code account} has paid this counterparty (an outbound DEBIT to
     * a never-before-seen payee — the precondition for the new-payee-drain typology). Records the
     * (account, counterparty) pair so subsequent transfers are no longer "new".
     */
    private boolean isFirstTimePayee(String account, TransactionReceived txn) {
        String counterparty = txn.counterpartyAccountId();
        if (!DEBIT.equals(txn.direction()) || counterparty == null) {
            return false;
        }
        String key = account + '|' + counterparty;
        boolean firstTime = payeeStore.get(key) == null;
        if (firstTime) {
            payeeStore.put(key, Boolean.TRUE);
        }
        return firstTime;
    }

    /** Count + sum of stored amounts for an account over the trailing window ending at {@code ts}. */
    private static Aggregate window(
            WindowStore<String, BigDecimal> store, String account, long ts, Duration window) {
        long from = ts - window.toMillis();
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        try (WindowStoreIterator<BigDecimal> it = store.fetch(account, from, ts)) {
            while (it.hasNext()) {
                count++;
                sum = sum.add(it.next().value);
            }
        }
        return new Aggregate(count, sum);
    }

    private record Aggregate(int count, BigDecimal sum) {}
}
