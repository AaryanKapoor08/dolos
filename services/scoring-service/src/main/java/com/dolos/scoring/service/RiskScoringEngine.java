package com.dolos.scoring.service;

import com.dolos.events.RiskScored;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.springframework.stereotype.Component;

/**
 * The scoring engine (Phase 2B): a thin bridge between a {@link ScoringFact} and the Drools rule set.
 * The AML typologies themselves live in {@code rules/aml.drl}, compiled from the classpath into a
 * {@link KieContainer} once at construction. For each fact the engine inserts it alongside a fresh
 * {@link ScoreAccumulator}, fires the stateless session, and packages the result as a
 * {@link RiskScored}.
 *
 * <p>This keeps the typologies editable without recompiling the topology, and keeps the engine
 * transport-independent — it never learns the facts came from Kafka Streams state stores. The window
 * constants below remain here because the streams adapter sizes its state stores from them; the
 * scoring thresholds now live in the DRL.
 */
@Component
public class RiskScoringEngine {

    /** The classic AML cash reporting threshold; the streams adapter uses it to flag sub-threshold deposits. */
    public static final BigDecimal REPORTING_THRESHOLD = new BigDecimal("10000");

    /** Structuring window: deposits that sum near/over the threshold within a day look deliberate. */
    public static final Duration STRUCTURING_WINDOW = Duration.ofDays(1);

    /** Velocity window: a flurry of transactions in a short window is itself suspicious. */
    public static final Duration VELOCITY_WINDOW = Duration.ofHours(1);

    private static final String SESSION = "amlSession";

    private final KieContainer kieContainer;

    public RiskScoringEngine() {
        // Scans the classpath for META-INF/kmodule.xml and compiles the DRL under rules/ at startup.
        this.kieContainer = KieServices.get().getKieClasspathContainer();
    }

    /** Scores one {@link ScoringFact} by firing the Drools rule set, into a {@link RiskScored} event. */
    public RiskScored score(ScoringFact fact) {
        ScoreAccumulator accumulator = new ScoreAccumulator();
        StatelessKieSession session = kieContainer.newStatelessKieSession(SESSION);
        session.execute(List.of(fact, accumulator));
        return new RiskScored(
                fact.getTransactionId(),
                fact.getAccountId(),
                accumulator.getScore(),
                accumulator.getReasons(),
                Instant.now());
    }
}
