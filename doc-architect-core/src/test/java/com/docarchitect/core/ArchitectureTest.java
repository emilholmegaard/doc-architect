package com.docarchitect.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ArchUnit tests to validate architectural rules and design patterns.
 *
 * <p>These tests ensure:
 * <ul>
 *   <li>Scanners extend appropriate base classes</li>
 *   <li>Package organization follows technology grouping</li>
 *   <li>Domain models are implemented as immutable records</li>
 *   <li>Base classes don't depend on implementations</li>
 *   <li>Layered architecture is respected</li>
 * </ul>
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.docarchitect.core");
    }

    /**
     * Verifies all scanner implementations extend AbstractScanner or one of its subclasses.
     * This ensures consistent behavior and proper implementation of the Scanner SPI.
     */
    @Test
    void scanners_shouldExtendAbstractScanner() {
        ArchRule rule = classes()
            .that().resideInAPackage("..scanner.impl..")
            .and().haveSimpleNameEndingWith("Scanner")
            .should().beAssignableTo("com.docarchitect.core.scanner.base.AbstractScanner")
            .orShould().beInterfaces();

        rule.check(classes);
    }

    /**
     * Verifies scanner implementations are organized by technology (java, python, dotnet, etc.).
     * This ensures maintainability and clear ownership of scanner implementations.
     */
    @Test
    void scanners_shouldBeInTechnologyPackages() {
        ArchRule rule = classes()
            .that().resideInAPackage("..scanner.impl..")
            .and().haveSimpleNameEndingWith("Scanner")
            .should().resideInAnyPackage("..java..", "..python..", "..dotnet..", "..javascript..", "..go..", "..schema..");

        rule.check(classes);
    }

    /**
     * Verifies all domain models in the model package are implemented as Java records.
     * Records provide immutability, compact constructor validation, and generated equals/hashCode.
     */
    @Test
    void models_shouldBeRecords() {
        ArchRule rule = classes()
            .that().resideInAPackage("..model..")
            .and().areTopLevelClasses()
            .and().areNotEnums()
            .should().beRecords();

        rule.check(classes);
    }

    /**
     * Verifies base scanner classes don't depend on implementation classes.
     * This ensures proper abstraction and prevents circular dependencies.
     */
    @Test
    void baseScanners_shouldNotDependOnImplementations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..scanner.base..")
            .should().dependOnClassesThat().resideInAPackage("..scanner.impl..");

        rule.check(classes);
    }

    /**
     * Verifies utility classes don't depend on scanner implementations.
     * Utilities should be low-level, reusable components with no domain dependencies.
     */
    @Test
    void utilClasses_shouldNotDependOnScanners() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..util..")
            .should().dependOnClassesThat().resideInAPackage("..scanner..");

        rule.check(classes);
    }

    /**
     * Verifies model layer has no dependencies on scanner/generator/renderer implementations.
     * This ensures domain models remain independent and reusable.
     */
    @Test
    void models_shouldNotDependOnImplementations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAnyPackage("..scanner..", "..generator..", "..renderer..");

        rule.check(classes);
    }
}
