package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for Spring Framework component annotations.
 *
 * <p>Extracts components from Spring stereotype annotations:
 * <ul>
 *   <li>{@code @Service} - Business service components</li>
 *   <li>{@code @Component} - Generic Spring components</li>
 *   <li>{@code @RestController} - REST API controllers</li>
 *   <li>{@code @Controller} - MVC controllers</li>
 *   <li>{@code @Repository} - Data access components</li>
 *   <li>{@code @Configuration} - Configuration classes</li>
 * </ul>
 *
 * <p>This scanner enables framework-level component extraction beyond build file modules,
 * providing visibility into Spring-based architectural components.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *     // Business logic
 * }
 * }</pre>
 * <p>This creates a Component with type=SERVICE, name="UserService"</p>
 *
 * <p><b>Priority:</b> 15 (after dependency scanners, before API scanners)</p>
 *
 * @see Component
 * @see ComponentType
 * @since 1.0.0
 */
public class SpringComponentScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "spring-components";
    private static final String SCANNER_DISPLAY_NAME = "Spring Component Scanner";
    private static final String JAVA_FILE_PATTERN = "**/*.java";
    private static final int SCANNER_PRIORITY = 15;

    // Spring stereotype annotations
    private static final Set<String> COMPONENT_ANNOTATIONS = Set.of(
        "Service",
        "Component",
        "Repository",
        "Configuration"
    );

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
        "RestController",
        "Controller"
    );

    private static final String SPRING_ANNOTATION_PREFIX = "org.springframework";
    private static final String TECHNOLOGY = "Spring Framework";

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
        return Set.of(Technologies.JAVA, Technologies.KOTLIN);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(JAVA_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Only apply if Maven/Gradle dependency scanner found Spring dependencies
        return context.previousResults().values().stream()
            .flatMap(result -> result.dependencies().stream())
            .anyMatch(dep -> dep.artifactId().contains("spring"));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Spring Framework components in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        Set<String> processedComponents = new HashSet<>();

        // Find all Java files
        List<Path> javaFiles = context.findFiles(JAVA_FILE_PATTERN).toList();

        if (javaFiles.isEmpty()) {
            log.debug("No Java files found");
            return emptyResult();
        }

        for (Path javaFile : javaFiles) {
            try {
                parseJavaFile(javaFile).ifPresent(cu ->
                    extractSpringComponents(cu, javaFile, context, components, processedComponents)
                );
            } catch (Exception e) {
                log.warn("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.info("Found {} Spring components", components.size());

        return buildSuccessResult(
            components,
            List.of(), // No dependencies
            List.of(), // No API endpoints (handled by SpringRestApiScanner)
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Extracts Spring components from a compilation unit.
     *
     * @param cu parsed compilation unit
     * @param filePath path to source file
     * @param context scan context
     * @param components list to add discovered components
     * @param processedComponents set of already processed component names (for deduplication)
     */
    private void extractSpringComponents(CompilationUnit cu, Path filePath, ScanContext context,
                                        List<Component> components, Set<String> processedComponents) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (classDecl.isInterface()) {
                return; // Skip interfaces
            }

            String className = classDecl.getNameAsString();
            ComponentType componentType = determineComponentType(classDecl);

            if (componentType != null && !processedComponents.contains(className)) {
                String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

                String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
                String componentId = IdGenerator.generate("spring-component", fullyQualifiedName);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("fullyQualifiedName", fullyQualifiedName);
                metadata.put("package", packageName);
                metadata.put("sourceFile", context.rootPath().relativize(filePath).toString());

                Component component = new Component(
                    componentId,
                    className,
                    componentType,
                    "Spring " + componentType.name().toLowerCase() + ": " + className,
                    TECHNOLOGY,
                    filePath.getParent().toString(),
                    metadata
                );

                components.add(component);
                processedComponents.add(className);

                log.debug("Found Spring component: {} (type={})", className, componentType);
            }
        });
    }

    /**
     * Determines the component type based on Spring annotations.
     *
     * @param classDecl class declaration
     * @return component type, or null if not a Spring component
     */
    private ComponentType determineComponentType(ClassOrInterfaceDeclaration classDecl) {
        for (AnnotationExpr annotation : classDecl.getAnnotations()) {
            String annotationName = annotation.getNameAsString();

            if (CONTROLLER_ANNOTATIONS.contains(annotationName)) {
                return ComponentType.SERVICE; // Controllers are service-layer components
            }

            if ("Service".equals(annotationName)) {
                return ComponentType.SERVICE;
            }

            if ("Repository".equals(annotationName)) {
                return ComponentType.MODULE; // Data access layer
            }

            if ("Component".equals(annotationName) || "Configuration".equals(annotationName)) {
                return ComponentType.MODULE; // Generic components
            }
        }

        return null; // Not a Spring component
    }
}
