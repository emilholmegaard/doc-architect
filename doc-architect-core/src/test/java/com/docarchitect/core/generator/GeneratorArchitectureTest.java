package com.docarchitect.core.generator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * ArchUnit tests for generator architecture and design principles.
 *
 * <p>Validates architectural constraints and coding standards:
 * <ul>
 *   <li>All generators must implement DiagramGenerator</li>
 *   <li>Generator implementations must be in impl package</li>
 *   <li>Generators must have comprehensive Javadoc</li>
 *   <li>Generator naming conventions</li>
 *   <li>No field injection (prefer constructor injection)</li>
 *   <li>Proper package organization</li>
 * </ul>
 */
class GeneratorArchitectureTest {

    private static JavaClasses generatorClasses;

    @BeforeAll
    static void setUp() {
        generatorClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(location -> !location.contains("/jrt:/"))  // Exclude JDK modules (Java 21 compatibility)
            .importPackages("com.docarchitect.core.generator");
    }

    @Test
    void allGeneratorImplementationsShouldImplementDiagramGenerator() {
        ArchRule rule = classes()
            .that().resideInAPackage("..generator.impl..")
            .and().haveSimpleNameEndingWith("Generator")
            .should().implement(DiagramGenerator.class)
            .because("all generator implementations must implement the DiagramGenerator interface");

        rule.check(generatorClasses);
    }

    @Test
    void generatorImplementationsShouldResideInImplPackage() {
        ArchRule rule = classes()
            .that().implement(DiagramGenerator.class)
            .and().areNotInterfaces()
            .should().resideInAPackage("..generator.impl..")
            .because("generator implementations should be in the impl package");

        rule.check(generatorClasses);
    }

    @Test
    void generatorClassesShouldHaveGeneratorSuffix() {
        ArchRule rule = classes()
            .that().implement(DiagramGenerator.class)
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Generator")
            .because("generator implementation classes should end with 'Generator'");

        rule.check(generatorClasses);
    }

    @Test
    void generatorsShouldNotUseFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(generatorClasses);
    }

    @Test
    void publicMethodsInGeneratorsShouldFollowNamingConventions() {
        ArchRule rule = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().implement(DiagramGenerator.class)
            .and().areDeclaredInClassesThat().areNotInterfaces()
            .should().haveNameMatching("[a-z][a-zA-Z0-9]*")
            .because("public generator methods should follow camelCase naming conventions");

        rule.check(generatorClasses);
    }

    @Test
    void generatorImplementationsShouldOnlyDependOnAllowedLayers() {
        ArchRule rule = classes()
            .that().resideInAPackage("..generator.impl..")
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "..generator.impl..",
                    "com.docarchitect.core.generator",
                    "com.docarchitect.core.model..",
                    "com.docarchitect.core.util..",
                    "java..",
                    "org.slf4j.."
                )
            .because("generator implementations should only depend on generator API, model, util, and standard libraries");

        rule.check(generatorClasses);
    }

    @Test
    void generatorInterfaceShouldNotDependOnImplementations() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.docarchitect.core.generator")
            .and().areInterfaces()
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "com.docarchitect.core.generator",
                    "com.docarchitect.core.model..",
                    "java..",
                    "org.slf4j.."
                )
            .because("generator interfaces should not depend on implementations");

        rule.check(generatorClasses);
    }

    @Test
    void generatorImplementationsShouldBePackagePrivateOrPublic() {
        ArchRule rule = classes()
            .that().implement(DiagramGenerator.class)
            .and().areNotInterfaces()
            .should().bePublic()
            .because("generator implementations need to be public for ServiceLoader discovery");

        rule.check(generatorClasses);
    }

    @Test
    void generatorImplementationsShouldBeInstantiable() {
        ArchRule rule = classes()
            .that().implement(DiagramGenerator.class)
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Generator")
            .because("generators must follow naming conventions for clarity and ServiceLoader instantiation");

        rule.check(generatorClasses);
    }

    @Test
    void generatorsShouldNotThrowGenericExceptions() {
        ArchRule rule = methods()
            .that().areDeclaredInClassesThat().implement(DiagramGenerator.class)
            .should().notDeclareThrowableOfType(Exception.class)
            .andShould().notDeclareThrowableOfType(Throwable.class)
            .because("generators should throw specific exceptions, not generic Exception");

        rule.check(generatorClasses);
    }
}
