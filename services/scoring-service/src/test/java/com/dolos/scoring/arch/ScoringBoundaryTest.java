package com.dolos.scoring.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Phase 1F architecture baseline. scoring-service has no controller or repository — it is a pure
 * consume → score → publish path. The meaningful boundary is that the scoring domain logic (the
 * {@code service} package, e.g. {@code RiskScoringEngine}) stays independent of the Kafka transport
 * adapter (the {@code messaging} and {@code config} packages): the rules must not know how they are
 * delivered. That keeps the engine unit-testable in isolation and swappable onto Kafka Streams in
 * Phase 2A without touching the rules.
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
                    .resideInAnyPackage("..messaging..", "..config..");
}
