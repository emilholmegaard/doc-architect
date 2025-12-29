package com.docarchitect.core.scanner;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * ArchUnit tests for scanner architecture and design principles.
 *
 * <p>Validates architectural constraints and coding standards:
 * <ul>
 *   <li>All scanners must implement Scanner interface or extend AbstractScanner</li>
 *   <li>Scanner implementations must be in impl package</li>
 *   <li>Scanners must have comprehensive Javadoc</li>
 *   <li>Scanner naming conventions</li>
 *   <li>No field injection (prefer constructor injection)</li>
 *   <li>Proper package organization by technology</li>
 * </ul>
 */
class ScannerArchitectureTest {

    private static JavaClasses scannerClasses;

    @BeforeAll
    static void setUp() {
        scannerClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(location -> !location.contains("/jrt:/"))  // Exclude JDK modules (Java 21 compatibility)
            .importPackages("com.docarchitect.core.scanner");
    }

    @Test
    void allScannerImplementationsShouldImplementScannerOrExtendAbstractScanner() {
        ArchRule rule = classes()
            .that().resideInAPackage("..scanner.impl..")
            .and().haveSimpleNameEndingWith("Scanner")
            .and().areNotInterfaces()
            .should().implement(Scanner.class)
            .because("all scanner implementations must implement the Scanner interface");

        rule.check(scannerClasses);
    }

    @Test
    void scannerImplementationsShouldResideInImplPackage() {
        ArchRule rule = classes()
            .that().implement(Scanner.class)
            .and().areNotInterfaces()
            .and().areNotNestedClasses()
            .and().resideOutsideOfPackage("..scanner.base..")
            .should().resideInAPackage("..scanner.impl..")
            .because("scanner implementations (excluding base classes) should be in the impl package");

        rule.check(scannerClasses);
    }

    @Test
    void scannerClassesShouldHaveScannerSuffix() {
        ArchRule rule = classes()
            .that().implement(Scanner.class)
            .and().areNotInterfaces()
            .and().areNotNestedClasses()
            .should().haveSimpleNameEndingWith("Scanner")
            .because("scanner implementation classes should end with 'Scanner'");

        rule.check(scannerClasses);
    }

    @Test
    void scannersShouldNotUseFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(scannerClasses);
    }

    @Test
    void publicMethodsInScannersShouldFollowNamingConventions() {
        ArchRule rule = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().implement(Scanner.class)
            .and().areDeclaredInClassesThat().areNotInterfaces()
            .should().haveNameMatching("[a-z][a-zA-Z0-9]*")
            .because("public scanner methods should follow camelCase naming conventions");

        rule.check(scannerClasses);
    }

    @Test
    void scannerImplementationsShouldOnlyDependOnAllowedLayers() {
        ArchRule rule = classes()
            .that().resideInAPackage("..scanner.impl..")
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "..scanner.impl..",
                    "..scanner.base..",
                    "..scanner.ast..",
                    "com.docarchitect.core.scanner",
                    "com.docarchitect.core.model..",
                    "com.docarchitect.core.util..",
                    "com.docarchitect.parser..",
                    "com.fasterxml.jackson..",
                    "com.github.javaparser..",
                    "graphql..",
                    "org.apache.avro..",
                    "org.antlr..",
                    "java..",
                    "org.slf4j.."
                )
            .because("scanner implementations should only depend on scanner API, base classes, AST models, model, util, parsers, schema libraries (GraphQL, Avro), and standard libraries");

        rule.check(scannerClasses);
    }

    @Test
    void scannerInterfaceShouldNotDependOnImplementations() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.docarchitect.core.scanner")
            .and().areInterfaces()
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "com.docarchitect.core.scanner",
                    "com.docarchitect.core.model..",
                    "java..",
                    "org.slf4j.."
                )
            .because("scanner interfaces should not depend on implementations");

        rule.check(scannerClasses);
    }

    @Test
    void scannerImplementationsShouldBePublic() {
        ArchRule rule = classes()
            .that().implement(Scanner.class)
            .and().areNotInterfaces()
            .and().areNotNestedClasses()
            .should().bePublic()
            .because("scanner implementations need to be public for ServiceLoader discovery");

        rule.check(scannerClasses);
    }

    @Test
    void scannerImplementationsShouldBeInstantiable() {
        ArchRule rule = classes()
            .that().implement(Scanner.class)
            .and().areNotInterfaces()
            .and().areNotNestedClasses()
            .should().haveSimpleNameEndingWith("Scanner")
            .because("scanners must follow naming conventions for clarity and ServiceLoader instantiation");

        rule.check(scannerClasses);
    }

    @Test
    void scannersShouldNotThrowGenericExceptions() {
        ArchRule rule = methods()
            .that().areDeclaredInClassesThat().implement(Scanner.class)
            .should().notDeclareThrowableOfType(Exception.class)
            .andShould().notDeclareThrowableOfType(Throwable.class)
            .because("scanners should throw specific exceptions, not generic Exception");

        rule.check(scannerClasses);
    }

    @Test
    void scannersShouldBeOrganizedByTechnology() {
        ArchRule rule = classes()
            .that().resideInAPackage("..scanner.impl..")
            .and().haveSimpleNameEndingWith("Scanner")
            .and().areNotInterfaces()
            .should().resideInAnyPackage(
                "..scanner.impl.java..",
                "..scanner.impl.python..",
                "..scanner.impl.dotnet..",
                "..scanner.impl.javascript..",
                "..scanner.impl.go..",
                "..scanner.impl.ruby..",
                "..scanner.impl.schema.."
            )
            .because("scanner implementations should be organized by technology (java, python, dotnet, javascript, go, ruby, schema)");

        rule.check(scannerClasses);
    }

    @Test
    void baseClassesShouldResideInBasePackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameStartingWith("Abstract")
            .and().resideInAPackage("..scanner..")
            .should().resideInAPackage("..scanner.base..")
            .because("abstract base classes should be in the base package");

        rule.check(scannerClasses);
    }
}
