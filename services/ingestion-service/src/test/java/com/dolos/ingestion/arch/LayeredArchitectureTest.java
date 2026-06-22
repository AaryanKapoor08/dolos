package com.dolos.ingestion.arch;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Phase 1F architecture baseline. ingestion-service is the reactive edge: it has no JPA repository
 * (it persists raw rows via R2DBC) and no inbound Kafka listener, so the layering it enforces is
 * controller → service: the service is only ever entered through the controller. (The request DTO
 * lives under {@code ..api..} and the service maps from it, so the Controller layer is allowed to be
 * depended upon.)
 */
@AnalyzeClasses(
        packages = "com.dolos.ingestion",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layering =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Controller").definedBy("..api..")
                    .layer("Service").definedBy("..service..")
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller");
}
