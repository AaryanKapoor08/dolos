package com.dolos.alert.arch;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Phase 1F architecture baseline. Enforces the controller → service → repository layering:
 * the repository is reachable only from the service, and the service only from the controller or
 * the Kafka listener. In particular a controller can never touch the repository directly.
 *
 * <p>{@code consideringOnlyDependenciesInLayers()} scopes the check to these layers, so legitimate
 * dependencies on dolos-common/dolos-events, the entity, or the framework are ignored. The api DTOs
 * (the request/response contract) live under {@code ..api..}, and the service maps to/from them, so
 * the Controller layer is intentionally allowed to be depended upon — the invariant that matters is
 * that nothing but the service reaches the repository.
 */
@AnalyzeClasses(packages = "com.dolos.alert", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layering =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Controller").definedBy("..api..")
                    .layer("Messaging").definedBy("..messaging..")
                    .layer("Service").definedBy("..service..")
                    .layer("Repository").definedBy("..repo..")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Messaging");
}
