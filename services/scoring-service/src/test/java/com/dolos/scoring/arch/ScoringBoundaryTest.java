package com.dolos.scoring.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Phase 1F architecture baseline (kept current through Phase 2A). scoring-service has no controller
 * or repository — it is a pure consume → score → publish path, now a Kafka Streams topology. The
 * meaningful boundary is that the scoring domain logic (the {@code service} package, e.g.
 * {@code RiskScoringEngine} and {@code ScoringFact}) stays independent of the Streams transport
 * adapter (the {@code streams} and {@code config} packages): the rules must not know how they are
 * delivered. That kept the engine unit-testable in isolation and let it move onto Kafka Streams in
 * Phase 2A without changing the rules, and lets Phase 2B swap them for Drools the same way.
 */
@AnalyzeClasses(packages = "com.dolos.scoring", importOptions = ImportOption.DoNotIncludeTests.class)
class ScoringBoundaryTest {

    @ArchTest
    static final ArchRule engineIsTransportIndependent =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..streams..", "..config..");
}
