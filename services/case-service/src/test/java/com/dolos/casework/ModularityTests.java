package com.dolos.casework;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Phase 3A — Spring Modulith module model checks.
 *
 * <p>{@link #verifiesModuleBoundaries()} fails the build if any package dependency crosses a module
 * boundary the wrong way (e.g. {@code casecmd} reaching into {@code casequery}, or a use of a module's
 * internal package rather than its exposed API). The intended directions are declared on each module's
 * {@code package-info} via {@code @ApplicationModule(allowedDependencies = ...)}.
 *
 * <p>{@link #writesModuleDocumentation()} generates the C4/PlantUML component diagrams and the
 * per-module "canvas" under {@code build/spring-modulith-docs}, so the architecture is documented
 * straight from the code.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(CaseServiceApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    @SuppressWarnings("removal") // the (modules, outputFolder) ctor is deprecated-for-removal but is
    // still the simplest way to redirect output under build/; revisit when the Options API stabilizes.
    void writesModuleDocumentation() {
        new Documenter(modules, "build/spring-modulith-docs").writeDocumentation();
    }
}
