package com.dolos.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Guards the {@link ArchitectureTest} suite against silently passing vacuously. The rules are only
 * meaningful if every module's classes are actually on the analysis classpath — a build change that
 * dropped the module dependencies would leave the ArchUnit rules green over an empty class set. This
 * test fails loudly if the whole {@code com.dolos} graph didn't import.
 */
class ArchitectureImportSanityTest {

    @Test
    void theWholeMonorepoIsOnTheAnalysisClasspath() {
        JavaClasses classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.dolos");

        // The 14 services + 4 libs are well over 200 production classes; a threshold this low still
        // catches a classpath regression while staying robust to normal growth/shrinkage.
        assertTrue(
                classes.size() > 200,
                () -> "expected the aggregated monorepo classes to be imported for analysis, but found "
                        + classes.size());
    }
}
