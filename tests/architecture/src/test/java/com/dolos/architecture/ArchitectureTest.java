package com.dolos.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Monorepo-wide architecture rules (Phase 6E) — the single shared suite that replaced the per-service
 * ArchUnit baselines from Phase 1F. It imports every {@code com.dolos} production class across all 14
 * services + 4 libraries (a service's own module-local checks, e.g. case-service's Spring Modulith
 * {@code ModularityTests}, stay in that module).
 *
 * <p>Because it sees the whole graph at once, a merged layered rule and a module-isolation rule apply
 * uniformly: they hold monorepo-wide precisely because no service depends on another service's
 * internals — everything shared flows through the {@code dolos-*} libraries.
 */
@AnalyzeClasses(packages = "com.dolos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * The four shared libraries every service is allowed to depend on. Dependencies whose TARGET is one
     * of these are ignored by the module-isolation rule (they are the intended shared surface — the
     * event contract, common utilities, the security starter, and the gRPC stubs).
     */
    private static final String[] SHARED_LIBS = {
        "com.dolos.common..", "com.dolos.events..", "com.dolos.security..", "com.dolos.proto.."
    };

    /**
     * Module isolation: no service or library may reach into another module's internals. Each
     * {@code com.dolos.<module>..} package is a slice; the only permitted cross-slice dependencies are
     * those onto the shared {@code dolos-*} libraries, which are ignored here.
     */
    @ArchTest
    static final ArchRule modules_do_not_depend_on_each_other =
            slices()
                    .matching("com.dolos.(*)..")
                    .as("Dolos modules")
                    .should()
                    .notDependOnEachOther()
                    .ignoreDependency(alwaysTrue(), resideInAnyPackage(SHARED_LIBS));

    /**
     * Layering: controllers ({@code ..api..}) and Kafka listeners ({@code ..messaging..}) drive the
     * service ({@code ..service..}), which is the only layer allowed to reach the repository
     * ({@code ..repo..}). {@code consideringOnlyDependenciesInLayers()} scopes the check to these four
     * packages, so a service whose shape differs (e.g. case-service's CQRS packages, the copilot's
     * feature packages) is simply not constrained here.
     */
    @ArchTest
    static final ArchRule layering_is_respected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Api").definedBy("..api..")
                    .layer("Messaging").definedBy("..messaging..")
                    .layer("Service").definedBy("..service..")
                    .layer("Repository").definedBy("..repo..")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Api", "Messaging");

    /** DTOs at the boundary: the API layer must never reach the persistence layer directly. */
    @ArchTest
    static final ArchRule api_layer_does_not_touch_persistence =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..repo..")
                    .as("boundary DTOs/controllers (..api..) must not depend on the persistence layer (..repo..)")
                    .allowEmptyShould(true);

    /**
     * The scoring domain/rules must stay independent of the transport that delivers transactions — the
     * invariant that let the engine move onto Kafka Streams (2A) and Drools (2B) without change.
     */
    @ArchTest
    static final ArchRule scoring_engine_is_transport_independent =
            noClasses()
                    .that()
                    .resideInAPackage("com.dolos.scoring.service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..streams..", "..config..", "..grpc..")
                    .as("the scoring domain/rules must not know how transactions are delivered")
                    .allowEmptyShould(true);

    /** Constructor injection only (the Dolos convention) — no {@code @Autowired}/{@code @Value} fields. */
    @ArchTest
    static final ArchRule uses_constructor_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** slf4j (via Logback) everywhere — never {@code java.util.logging}. */
    @ArchTest static final ArchRule uses_slf4j_not_jul = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /** No {@code System.out}/{@code System.err} — logging goes through the structured JSON appender. */
    @ArchTest static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
