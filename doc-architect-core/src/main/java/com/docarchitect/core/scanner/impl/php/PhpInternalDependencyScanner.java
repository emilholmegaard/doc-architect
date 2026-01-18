package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for detecting internal dependencies between PHP classes.
 *
 * <p>This scanner analyzes PHP source files to identify internal dependencies including:
 * <ul>
 *   <li><b>Use statements</b> - Namespace imports ({@code use App\Service\UserService})</li>
 *   <li><b>Constructor injection</b> - Dependencies injected via constructor parameters</li>
 *   <li><b>Method injection</b> - Dependencies injected via method parameters</li>
 *   <li><b>Trait usage</b> - Traits included in classes ({@code use LoggerTrait})</li>
 *   <li><b>Class inheritance</b> - Parent classes ({@code extends BaseController})</li>
 *   <li><b>Interface implementations</b> - Implemented interfaces ({@code implements Countable})</li>
 *   <li><b>Laravel service container bindings</b> - Container bindings in service providers</li>
 * </ul>
 *
 * <p><b>Namespace Resolution:</b> The scanner resolves short class names to fully-qualified
 * names using the use statements found in the file. For example, if a file contains
 * {@code use App\Models\User;} and then references {@code User}, the scanner will
 * resolve this to {@code App\Models\User}.
 *
 * <p><b>Vendor Exclusion:</b> Files in the {@code vendor/} directory are automatically
 * excluded from scanning to avoid analyzing third-party dependencies.
 *
 * <p><b>Output:</b> The scanner produces:
 * <ul>
 *   <li>{@link Component} records for each discovered PHP class</li>
 *   <li>{@link Relationship} records for each detected dependency</li>
 * </ul>
 *
 * <p><b>Priority:</b> 70 (after component and ORM scanners)</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * <?php
 * namespace App\Controller;
 *
 * use App\Service\UserService;
 * use App\Traits\LoggerTrait;
 *
 * class UserController extends BaseController implements JsonSerializable
 * {
 *     use LoggerTrait;
 *
 *     public function __construct(private UserService $userService) {}
 * }
 * }</pre>
 *
 * <p>This will detect relationships:</p>
 * <ul>
 *   <li>Import: UserController → App\Service\UserService</li>
 *   <li>Injection: UserController → App\Service\UserService</li>
 *   <li>Trait: UserController → LoggerTrait</li>
 *   <li>Inheritance: UserController → BaseController</li>
 *   <li>Interface: UserController → JsonSerializable</li>
 * </ul>
 *
 * @see Component
 * @see Relationship
 * @see RelationshipType
 * @since 1.0.0
 */
public class PhpInternalDependencyScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "php-internal-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "PHP Internal Dependency Scanner";
    private static final String PATTERN_PHP_FILES = "**/*.php";
    private static final int SCANNER_PRIORITY = 70;

    private static final String TECHNOLOGY = "PHP";
    private static final String DEPENDENCY_TYPE_IMPORT = "import";
    private static final String DEPENDENCY_TYPE_INJECTION = "injection";
    private static final String DEPENDENCY_TYPE_TRAIT = "trait";
    private static final String DEPENDENCY_TYPE_INHERITANCE = "inheritance";
    private static final String DEPENDENCY_TYPE_INTERFACE = "interface";
    private static final String DEPENDENCY_TYPE_BINDING = "binding";

    /**
     * Regex to extract namespace declaration.
     * Matches: namespace App\Controller;
     * Captures: (1) namespace
     */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
        "namespace\\s+([A-Za-z_][A-Za-z0-9_\\\\]*)"
    );

    /**
     * Regex to extract use statements (imports).
     * Matches: use App\Service\UserService; or use App\Service\UserService as Alias;
     * Captures: (1) fully qualified class name, (2) optional alias
     */
    private static final Pattern USE_STATEMENT_PATTERN = Pattern.compile(
        "^\\s*use\\s+([A-Za-z_][A-Za-z0-9_\\\\]+)(?:\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*))?\\s*;",
        Pattern.MULTILINE
    );

    /**
     * Regex to extract class/interface/trait definitions.
     * Matches: class UserController extends BaseController implements JsonSerializable
     * Captures: (1) class type (class/interface/trait), (2) class name, (3) extends clause, (4) implements clause
     */
    private static final Pattern CLASS_DEFINITION_PATTERN = Pattern.compile(
        "(?:abstract\\s+|final\\s+)?(class|interface|trait)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+extends\\s+([A-Za-z_][A-Za-z0-9_\\\\]*))?(?:\\s+implements\\s+([A-Za-z_][A-Za-z0-9_,\\s\\\\]*))?",
        Pattern.MULTILINE
    );

    /**
     * Regex to extract trait use statements inside classes.
     * Matches: use LoggerTrait, AnotherTrait;
     * Captures: (1) comma-separated trait names
     */
    private static final Pattern TRAIT_USE_PATTERN = Pattern.compile(
        "\\{[^}]*?\\buse\\s+([A-Za-z_][A-Za-z0-9_,\\s\\\\]+)\\s*;",
        Pattern.DOTALL
    );

    /**
     * Regex to extract constructor parameters with type hints.
     * Matches: public function __construct(private UserService $userService, LoggerInterface $logger)
     * Captures groups iteratively: type hint and variable name pairs
     */
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile(
        "function\\s+__construct\\s*\\(([^)]+)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract typed parameters from constructor/method.
     * Matches: private UserService $userService or UserService $userService or ?UserService $userService
     * Captures: (1) type hint (may include ?), (2) variable name
     */
    private static final Pattern TYPED_PARAMETER_PATTERN = Pattern.compile(
        "(?:private|protected|public|readonly)?\\s*\\??([A-Za-z_][A-Za-z0-9_\\\\]*)\\s+\\$([A-Za-z_][A-Za-z0-9_]*)"
    );

    /**
     * Regex to extract method parameters with type hints (for method injection).
     * Matches: public function setLogger(LoggerInterface $logger)
     * Captures: (1) method name, (2) parameters
     */
    private static final Pattern METHOD_INJECTION_PATTERN = Pattern.compile(
        "public\\s+function\\s+set([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]+)\\)"
    );

    /**
     * Regex to extract Laravel container bindings.
     * Matches: $this->app->bind(UserServiceInterface::class, UserService::class)
     * Captures: (1) interface class, (2) implementation class
     */
    private static final Pattern CONTAINER_BIND_PATTERN = Pattern.compile(
        "\\$this->app->(?:bind|singleton)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_\\\\]*)::class\\s*,\\s*([A-Za-z_][A-Za-z0-9_\\\\]*)::class\\s*\\)"
    );

    /**
     * Built-in PHP types to exclude from dependency tracking.
     */
    private static final Set<String> BUILTIN_TYPES = Set.of(
        "string", "int", "float", "bool", "array", "object", "callable", "iterable",
        "mixed", "void", "null", "true", "false", "never", "self", "static", "parent"
    );

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.PHP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_PHP_FILES);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Apply if any PHP files exist
        return context.findFiles(PATTERN_PHP_FILES)
            .anyMatch(f -> !isVendorFile(f));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning PHP internal dependencies in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();

        // Find all PHP files, excluding vendor directory
        List<Path> phpFiles = context.findFiles(PATTERN_PHP_FILES)
            .filter(f -> !isVendorFile(f))
            .toList();

        for (Path phpFile : phpFiles) {
            try {
                scanPhpFile(phpFile, context, components, relationships, processedClasses);
            } catch (IOException e) {
                log.warn("Failed to scan PHP file: {} - {}", phpFile, e.getMessage());
            }
        }

        log.info("Found {} PHP classes and {} internal dependencies",
            components.size(), relationships.size());

        return buildSuccessResult(
            components,
            List.of(), // No external dependencies
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            relationships,
            List.of()  // No warnings
        );
    }

    /**
     * Scans a single PHP file for classes and their dependencies.
     *
     * @param file path to PHP file
     * @param context scan context
     * @param components list to add discovered components
     * @param relationships list to add discovered relationships
     * @param processedClasses set of already processed class names
     * @throws IOException if file cannot be read
     */
    private void scanPhpFile(Path file, ScanContext context, List<Component> components,
                             List<Relationship> relationships, Set<String> processedClasses) throws IOException {
        String content = readFileContent(file);

        // Extract namespace
        String namespace = extractNamespace(content);

        // Build import map for resolving short class names
        Map<String, String> imports = buildImportMap(content);

        // Extract class definition
        Matcher classMatcher = findFirst(CLASS_DEFINITION_PATTERN, content);
        if (classMatcher == null) {
            return; // Not a class file
        }

        String classType = classMatcher.group(1); // class, interface, or trait
        String className = classMatcher.group(2);
        String extendsClause = classMatcher.group(3);
        String implementsClause = classMatcher.group(4);

        String fullyQualifiedName = buildFullyQualifiedName(namespace, className);

        // Skip if already processed
        if (processedClasses.contains(fullyQualifiedName)) {
            return;
        }
        processedClasses.add(fullyQualifiedName);

        // Create component
        Component component = createComponent(fullyQualifiedName, className, classType, file, context);
        components.add(component);

        // Extract dependencies

        // 1. Use statement imports (but only track the ones that are actually used)
        for (Map.Entry<String, String> importEntry : imports.entrySet()) {
            String importedClass = importEntry.getValue();
            // Only add import relationship if the imported class is used in the file
            // beyond just the use statement itself
            String shortName = importEntry.getKey();
            if (isClassUsed(content, shortName, importedClass)) {
                relationships.add(createRelationship(
                    fullyQualifiedName, importedClass, DEPENDENCY_TYPE_IMPORT,
                    "Imports " + importedClass
                ));
            }
        }

        // 2. Class inheritance
        if (extendsClause != null && !extendsClause.isBlank()) {
            String parentClass = resolveClassName(extendsClause.trim(), namespace, imports);
            relationships.add(createRelationship(
                fullyQualifiedName, parentClass, DEPENDENCY_TYPE_INHERITANCE,
                "Extends " + parentClass
            ));
        }

        // 3. Interface implementations
        if (implementsClause != null && !implementsClause.isBlank()) {
            for (String interfaceName : implementsClause.split(",")) {
                String trimmed = interfaceName.trim();
                if (!trimmed.isEmpty()) {
                    String resolvedInterface = resolveClassName(trimmed, namespace, imports);
                    relationships.add(createRelationship(
                        fullyQualifiedName, resolvedInterface, DEPENDENCY_TYPE_INTERFACE,
                        "Implements " + resolvedInterface
                    ));
                }
            }
        }

        // 4. Trait usage
        extractTraitDependencies(content, fullyQualifiedName, namespace, imports, relationships);

        // 5. Constructor injection
        extractConstructorDependencies(content, fullyQualifiedName, namespace, imports, relationships);

        // 6. Method injection (setter injection)
        extractMethodInjectionDependencies(content, fullyQualifiedName, namespace, imports, relationships);

        // 7. Laravel container bindings
        extractContainerBindings(content, fullyQualifiedName, namespace, imports, relationships);
    }

    /**
     * Extracts the namespace from PHP file content.
     *
     * @param content file content
     * @return namespace or empty string if none
     */
    private String extractNamespace(String content) {
        Matcher matcher = findFirst(NAMESPACE_PATTERN, content);
        return matcher != null ? matcher.group(1) : "";
    }

    /**
     * Builds a map of short class names to fully qualified names from use statements.
     *
     * @param content file content
     * @return map of alias/short name to fully qualified name
     */
    private Map<String, String> buildImportMap(String content) {
        Map<String, String> imports = new HashMap<>();

        Matcher matcher = USE_STATEMENT_PATTERN.matcher(content);
        while (matcher.find()) {
            String fullyQualified = matcher.group(1);
            String alias = matcher.group(2);

            // Use alias if provided, otherwise use the short class name
            String shortName = alias != null ? alias : getShortClassName(fullyQualified);
            imports.put(shortName, fullyQualified);
        }

        return imports;
    }

    /**
     * Extracts trait dependencies from class content.
     *
     * @param content file content
     * @param sourceClass fully qualified name of the class
     * @param namespace current namespace
     * @param imports import map
     * @param relationships list to add relationships
     */
    private void extractTraitDependencies(String content, String sourceClass, String namespace,
                                          Map<String, String> imports, List<Relationship> relationships) {
        Matcher matcher = TRAIT_USE_PATTERN.matcher(content);
        while (matcher.find()) {
            String traits = matcher.group(1);
            for (String trait : traits.split(",")) {
                String trimmed = trait.trim();
                if (!trimmed.isEmpty() && !trimmed.contains("{")) { // Exclude trait conflict resolution syntax
                    String resolvedTrait = resolveClassName(trimmed, namespace, imports);
                    relationships.add(createRelationship(
                        sourceClass, resolvedTrait, DEPENDENCY_TYPE_TRAIT,
                        "Uses trait " + resolvedTrait
                    ));
                }
            }
        }
    }

    /**
     * Extracts constructor injection dependencies.
     *
     * @param content file content
     * @param sourceClass fully qualified name of the class
     * @param namespace current namespace
     * @param imports import map
     * @param relationships list to add relationships
     */
    private void extractConstructorDependencies(String content, String sourceClass, String namespace,
                                                Map<String, String> imports, List<Relationship> relationships) {
        Matcher constructorMatcher = findFirst(CONSTRUCTOR_PATTERN, content);
        if (constructorMatcher == null) {
            return;
        }

        String parameters = constructorMatcher.group(1);
        extractTypedParameters(parameters, sourceClass, namespace, imports, relationships, DEPENDENCY_TYPE_INJECTION);
    }

    /**
     * Extracts method injection dependencies (setter methods).
     *
     * @param content file content
     * @param sourceClass fully qualified name of the class
     * @param namespace current namespace
     * @param imports import map
     * @param relationships list to add relationships
     */
    private void extractMethodInjectionDependencies(String content, String sourceClass, String namespace,
                                                    Map<String, String> imports, List<Relationship> relationships) {
        Matcher matcher = METHOD_INJECTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String parameters = matcher.group(2);
            extractTypedParameters(parameters, sourceClass, namespace, imports, relationships, DEPENDENCY_TYPE_INJECTION);
        }
    }

    /**
     * Extracts typed parameters from a parameter list and creates relationships.
     *
     * @param parameters parameter list string
     * @param sourceClass fully qualified name of the class
     * @param namespace current namespace
     * @param imports import map
     * @param relationships list to add relationships
     * @param dependencyType type of dependency (injection, etc.)
     */
    private void extractTypedParameters(String parameters, String sourceClass, String namespace,
                                        Map<String, String> imports, List<Relationship> relationships,
                                        String dependencyType) {
        Matcher paramMatcher = TYPED_PARAMETER_PATTERN.matcher(parameters);
        while (paramMatcher.find()) {
            String typeHint = paramMatcher.group(1);

            // Skip built-in types
            if (isBuiltinType(typeHint)) {
                continue;
            }

            String resolvedType = resolveClassName(typeHint, namespace, imports);
            relationships.add(createRelationship(
                sourceClass, resolvedType, dependencyType,
                "Injects " + resolvedType
            ));
        }
    }

    /**
     * Extracts Laravel container bindings from service providers.
     *
     * @param content file content
     * @param sourceClass fully qualified name of the service provider
     * @param namespace current namespace
     * @param imports import map
     * @param relationships list to add relationships
     */
    private void extractContainerBindings(String content, String sourceClass, String namespace,
                                          Map<String, String> imports, List<Relationship> relationships) {
        Matcher matcher = CONTAINER_BIND_PATTERN.matcher(content);
        while (matcher.find()) {
            String interfaceClass = matcher.group(1);
            String implementationClass = matcher.group(2);

            String resolvedInterface = resolveClassName(interfaceClass, namespace, imports);
            String resolvedImplementation = resolveClassName(implementationClass, namespace, imports);

            // Create binding relationship from the implementation to the interface
            relationships.add(createRelationship(
                resolvedImplementation, resolvedInterface, DEPENDENCY_TYPE_BINDING,
                "Binds " + resolvedImplementation + " to " + resolvedInterface
            ));
        }
    }

    /**
     * Resolves a potentially short class name to a fully qualified name.
     *
     * @param className class name (may be short or fully qualified)
     * @param namespace current namespace
     * @param imports import map
     * @return fully qualified class name
     */
    private String resolveClassName(String className, String namespace, Map<String, String> imports) {
        // Remove leading backslash if present
        className = className.startsWith("\\") ? className.substring(1) : className;

        // If already fully qualified (contains backslash)
        if (className.contains("\\")) {
            return className;
        }

        // Check imports map
        if (imports.containsKey(className)) {
            return imports.get(className);
        }

        // Assume same namespace
        return namespace.isEmpty() ? className : namespace + "\\" + className;
    }

    /**
     * Extracts the short class name from a fully qualified name.
     *
     * @param fullyQualifiedName fully qualified class name
     * @return short class name
     */
    private String getShortClassName(String fullyQualifiedName) {
        int lastSlash = fullyQualifiedName.lastIndexOf('\\');
        return lastSlash >= 0 ? fullyQualifiedName.substring(lastSlash + 1) : fullyQualifiedName;
    }

    /**
     * Builds a fully qualified class name from namespace and class name.
     *
     * @param namespace namespace
     * @param className class name
     * @return fully qualified name
     */
    private String buildFullyQualifiedName(String namespace, String className) {
        return namespace.isEmpty() ? className : namespace + "\\" + className;
    }

    /**
     * Checks if a class name is a built-in PHP type.
     *
     * @param typeName type name to check
     * @return true if built-in type
     */
    private boolean isBuiltinType(String typeName) {
        return BUILTIN_TYPES.contains(typeName.toLowerCase());
    }

    /**
     * Checks if a file is in the vendor directory.
     *
     * @param file path to check
     * @return true if in vendor directory
     */
    private boolean isVendorFile(Path file) {
        return file.toString().contains("/vendor/") || file.toString().contains("\\vendor\\");
    }

    /**
     * Checks if an imported class is actually used in the file content.
     *
     * @param content file content
     * @param shortName short class name or alias
     * @param fullyQualified fully qualified name
     * @return true if the class appears to be used
     */
    private boolean isClassUsed(String content, String shortName, String fullyQualified) {
        // Check for usage patterns: new ClassName, ClassName::, extends ClassName, implements ClassName,
        // use TraitName, ClassName $var, etc.
        String usagePattern = "(?:new\\s+" + Pattern.quote(shortName) + "|" +
            Pattern.quote(shortName) + "\\s*::|" +
            "extends\\s+" + Pattern.quote(shortName) + "|" +
            "implements\\s+[^{]*" + Pattern.quote(shortName) + "|" +
            "use\\s+" + Pattern.quote(shortName) + "\\s*;|" +
            "\\?" + Pattern.quote(shortName) + "\\s+\\$|" +
            Pattern.quote(shortName) + "\\s+\\$)";

        return Pattern.compile(usagePattern).matcher(content).find();
    }

    /**
     * Creates a Component record for a PHP class.
     *
     * @param fullyQualifiedName fully qualified class name
     * @param className short class name
     * @param classType type (class, interface, trait)
     * @param file source file path
     * @param context scan context
     * @return Component record
     */
    private Component createComponent(String fullyQualifiedName, String className, String classType,
                                      Path file, ScanContext context) {
        ComponentType type = switch (classType.toLowerCase()) {
            case "interface" -> ComponentType.LIBRARY;
            case "trait" -> ComponentType.LIBRARY;
            default -> ComponentType.MODULE;
        };

        Map<String, String> metadata = new HashMap<>();
        metadata.put("fullyQualifiedName", fullyQualifiedName);
        metadata.put("classType", classType);
        metadata.put("sourceFile", context.rootPath().relativize(file).toString());

        return new Component(
            IdGenerator.generate("php-class", fullyQualifiedName),
            className,
            type,
            "PHP " + classType + ": " + className,
            TECHNOLOGY,
            file.getParent().toString(),
            metadata
        );
    }

    /**
     * Creates a Relationship record for a dependency.
     *
     * @param sourceId source component ID
     * @param targetId target component ID
     * @param dependencyType type of dependency
     * @param description description
     * @return Relationship record
     */
    private Relationship createRelationship(String sourceId, String targetId,
                                            String dependencyType, String description) {
        RelationshipType type = switch (dependencyType) {
            case DEPENDENCY_TYPE_INHERITANCE, DEPENDENCY_TYPE_INTERFACE -> RelationshipType.USES;
            default -> RelationshipType.DEPENDS_ON;
        };

        return new Relationship(
            sourceId,
            targetId,
            type,
            description + " (" + dependencyType + ")",
            TECHNOLOGY
        );
    }
}
