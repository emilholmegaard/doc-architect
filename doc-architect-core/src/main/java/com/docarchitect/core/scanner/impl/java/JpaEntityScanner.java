package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.scanner.base.RegexPatterns;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

/**
 * Scanner for JPA entity and Spring Data MongoDB document declarations in Java source files.
 *
 * <p>Uses JavaParser to extract entities, fields, and relationships from @Entity and @Document classes.
 * Supports both JPA (relational) and MongoDB (document) data models.
 *
 * @see com.docarchitect.core.scanner.Scanner
 * @see com.docarchitect.core.model.DataEntity
 * @since 1.0.0
 */
public class JpaEntityScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "jpa-entities";
    private static final String SCANNER_DISPLAY_NAME = "JPA Entity Scanner";
    private static final String JAVA_FILE_GLOB = "**/*.java";
    private static final int SCANNER_PRIORITY = 60;

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of("Entity", "Document");
    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String DOCUMENT_ANNOTATION = "Document";
    private static final String TABLE_ANNOTATION = "Table";
    private static final String TABLE_NAME_ATTRIBUTE = "name";
    private static final String COLLECTION_ATTRIBUTE = "collection";
    private static final String COLUMN_ANNOTATION = "Column";
    private static final String NULLABLE_ATTRIBUTE = "nullable";
    private static final String FALSE_LITERAL = "false";
    private static final String ID_ANNOTATION = "Id";

    private static final String DATA_ENTITY_TYPE_TABLE = "table";
    private static final String DATA_ENTITY_TYPE_COLLECTION = "collection";
    private static final String ENTITY_DESCRIPTION_PREFIX = "JPA Entity: ";
    private static final String DOCUMENT_DESCRIPTION_PREFIX = "MongoDB Document: ";
    private static final String RELATIONSHIP_DESCRIPTION_SUFFIX = " relationship";
    private static final String RELATIONSHIP_TECHNOLOGY = "JPA";

    private static final String LIST_TYPE_PATTERN = "List<(.+)>";
    private static final String SET_TYPE_PATTERN = "Set<(.+)>";
    private static final String COLLECTION_TYPE_PATTERN = "Collection<(.+)>";

    private static final Set<String> RELATIONSHIP_ANNOTATIONS = Set.of(
        "OneToMany", "ManyToMany", "ManyToOne", "OneToOne"
    );

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final Pattern TABLE_PATTERN =
        Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern COLLECTION_PATTERN =
        Pattern.compile("@Document\\s*\\(\\s*collection\\s*=\\s*[\"']([^\"']+)[\"']");

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
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(JAVA_FILE_GLOB);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasJavaFiles()
            .and(ApplicabilityStrategies.hasJpa()
                .or(ApplicabilityStrategies.hasFileContaining("javax.persistence", "jakarta.persistence", "@Entity")));
    }

    /**
     * Internal record to hold entity and its relationships.
     */
    private record EntityResult(DataEntity entity, List<Relationship> relationships) {}

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning JPA entities and MongoDB documents in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> javaFiles = context.findFiles(JAVA_FILE_GLOB).toList();
        statsBuilder.filesDiscovered(javaFiles.size());

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path javaFile : javaFiles) {
            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<EntityResult> result = parseWithFallback(
                javaFile,
                cu -> extractEntitiesFromAST(cu),
                createFallbackStrategy(),
                statsBuilder
            );

            if (result.isSuccess()) {
                for (EntityResult entityResult : result.getData()) {
                    dataEntities.add(entityResult.entity());
                    relationships.addAll(entityResult.relationships());
                }
            }
        }

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} data entities and {} relationships (success rate: {:.1f}%, overall parse rate: {:.1f}%)",
            dataEntities.size(), relationships.size(), statistics.getSuccessRate(), statistics.getOverallParseRate());

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            dataEntities,
            relationships,
            List.of(),
            statistics
        );
    }

    /**
     * Extracts entities from a parsed CompilationUnit using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param cu the parsed CompilationUnit
     * @return list of entity results (entities + relationships)
     */
    private List<EntityResult> extractEntitiesFromAST(CompilationUnit cu) {
        List<EntityResult> results = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            Optional<AnnotationExpr> entityAnnotation = classDecl.getAnnotations().stream()
                .filter(ann -> ENTITY_ANNOTATIONS.contains(ann.getNameAsString()))
                .findFirst();

            if (entityAnnotation.isEmpty()) {
                return;
            }

            String annotationType = entityAnnotation.get().getNameAsString();
            boolean isMongoDocument = DOCUMENT_ANNOTATION.equals(annotationType);

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            String tableName = isMongoDocument
                ? extractCollectionName(classDecl, className)
                : extractTableName(classDecl, className);
            String entityType = isMongoDocument ? DATA_ENTITY_TYPE_COLLECTION : DATA_ENTITY_TYPE_TABLE;
            String description = (isMongoDocument ? DOCUMENT_DESCRIPTION_PREFIX : ENTITY_DESCRIPTION_PREFIX) + className;

            List<DataEntity.Field> fields = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            String primaryKey = null;

            for (FieldDeclaration fieldDecl : classDecl.getFields()) {
                Optional<AnnotationExpr> relationshipAnnotation = fieldDecl.getAnnotations().stream()
                    .filter(ann -> RELATIONSHIP_ANNOTATIONS.contains(ann.getNameAsString()))
                    .findFirst();

                if (relationshipAnnotation.isPresent()) {
                    extractRelationship(fieldDecl, fullyQualifiedName, relationshipAnnotation.get(), relationships);
                } else {
                    DataEntity.Field field = extractField(fieldDecl);
                    if (field != null) {
                        fields.add(field);
                        if (isIdField(fieldDecl) && primaryKey == null) {
                            primaryKey = field.name();
                        }
                    }
                }
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                tableName,
                entityType,
                fields,
                primaryKey,
                description
            );

            results.add(new EntityResult(entity, relationships));
            log.debug("Found {} entity: {} -> {}: {}",
                isMongoDocument ? "MongoDB" : "JPA",
                fullyQualifiedName,
                entityType,
                tableName);
        });

        return results;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * <p>The fallback strategy uses regex patterns to extract:
     * <ul>
     *   <li>@Entity or @Document annotations</li>
     *   <li>@Table(name = "...") or @Document(collection = "...") annotations</li>
     *   <li>Class names and package declarations</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<EntityResult> createFallbackStrategy() {
        return (file, content) -> {
            List<EntityResult> results = new ArrayList<>();

            // Check if file contains entity annotations
            boolean isEntity = content.contains("@Entity");
            boolean isDocument = content.contains("@Document");

            if (!isEntity && !isDocument) {
                return results; // Not an entity file
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract table/collection name
            String tableName;
            String entityType;
            String description;

            if (isDocument) {
                tableName = extractCollectionNameFromContent(content, className);
                entityType = DATA_ENTITY_TYPE_COLLECTION;
                description = DOCUMENT_DESCRIPTION_PREFIX + className;
            } else {
                tableName = extractTableNameFromContent(content, className);
                entityType = DATA_ENTITY_TYPE_TABLE;
                description = ENTITY_DESCRIPTION_PREFIX + className;
            }

            // Extract basic fields (simplified - no detailed field parsing)
            List<DataEntity.Field> fields = extractFieldsFromContent(content);

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                tableName,
                entityType,
                fields,
                null, // primary key unknown in fallback
                description
            );

            results.add(new EntityResult(entity, List.of())); // No relationships in fallback
            log.debug("Fallback parsing found entity: {} -> {}", fullyQualifiedName, tableName);
            return results;
        };
    }

    /**
     * Extracts table name from @Table annotation or defaults to snake_case.
     */
    private String extractTableNameFromContent(String content, String className) {
        Matcher matcher = TABLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return toSnakeCase(className);
    }

    /**
     * Extracts collection name from @Document annotation or defaults to snake_case.
     */
    private String extractCollectionNameFromContent(String content, String className) {
        Matcher matcher = COLLECTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return toSnakeCase(className);
    }

    /**
     * Extracts basic field information using regex (simplified).
     */
    private List<DataEntity.Field> extractFieldsFromContent(String content) {
        List<DataEntity.Field> fields = new ArrayList<>();

        // Use shared field pattern from RegexPatterns
        Matcher matcher = RegexPatterns.FIELD_PATTERN.matcher(content);

        while (matcher.find()) {
            String fieldType = matcher.group(1);
            String fieldName = matcher.group(2);

            // Skip fields that look like relationships (List, Set, Collection)
            if (fieldType.startsWith("List<") || fieldType.startsWith("Set<") ||
                fieldType.startsWith("Collection<")) {
                continue;
            }

            fields.add(new DataEntity.Field(fieldName, fieldType, true, null));
        }

        return fields;
    }

    /**
     * @deprecated Use {@link #extractEntitiesFromAST(CompilationUnit)} instead
     */
    @Deprecated
    private void parseJpaEntities(Path javaFile, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
        if (cuOpt.isEmpty()) {
            return;
        }

        CompilationUnit cu = cuOpt.get();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            Optional<AnnotationExpr> entityAnnotation = classDecl.getAnnotations().stream()
                .filter(ann -> ENTITY_ANNOTATIONS.contains(ann.getNameAsString()))
                .findFirst();

            if (entityAnnotation.isEmpty()) {
                return;
            }

            String annotationType = entityAnnotation.get().getNameAsString();
            boolean isMongoDocument = DOCUMENT_ANNOTATION.equals(annotationType);

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            String tableName = isMongoDocument
                ? extractCollectionName(classDecl, className)
                : extractTableName(classDecl, className);
            String entityType = isMongoDocument ? DATA_ENTITY_TYPE_COLLECTION : DATA_ENTITY_TYPE_TABLE;
            String description = (isMongoDocument ? DOCUMENT_DESCRIPTION_PREFIX : ENTITY_DESCRIPTION_PREFIX) + className;

            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            for (FieldDeclaration fieldDecl : classDecl.getFields()) {
                Optional<AnnotationExpr> relationshipAnnotation = fieldDecl.getAnnotations().stream()
                    .filter(ann -> RELATIONSHIP_ANNOTATIONS.contains(ann.getNameAsString()))
                    .findFirst();

                if (relationshipAnnotation.isPresent()) {
                    extractRelationship(fieldDecl, fullyQualifiedName, relationshipAnnotation.get(), relationships);
                } else {
                    DataEntity.Field field = extractField(fieldDecl);
                    if (field != null) {
                        fields.add(field);
                        if (isIdField(fieldDecl) && primaryKey == null) {
                            primaryKey = field.name();
                        }
                    }
                }
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                tableName,
                entityType,
                fields,
                primaryKey,
                description
            );

            dataEntities.add(entity);
            log.debug("Found {} entity: {} -> {}: {}",
                isMongoDocument ? "MongoDB" : "JPA",
                fullyQualifiedName,
                entityType,
                tableName);
        });
    }

    private String extractTableName(ClassOrInterfaceDeclaration classDecl, String className) {
        return classDecl.getAnnotations().stream()
            .filter(ann -> TABLE_ANNOTATION.equals(ann.getNameAsString()))
            .filter(ann -> ann instanceof NormalAnnotationExpr)
            .findFirst()
            .flatMap(ann -> ((NormalAnnotationExpr) ann).getPairs().stream()
                .filter(pair -> TABLE_NAME_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> pair.getValue().toString().replaceAll("\"", "")))
            .orElse(toSnakeCase(className));
    }

    private String extractCollectionName(ClassOrInterfaceDeclaration classDecl, String className) {
        return classDecl.getAnnotations().stream()
            .filter(ann -> DOCUMENT_ANNOTATION.equals(ann.getNameAsString()))
            .filter(ann -> ann instanceof NormalAnnotationExpr)
            .findFirst()
            .flatMap(ann -> ((NormalAnnotationExpr) ann).getPairs().stream()
                .filter(pair -> COLLECTION_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> pair.getValue().toString().replaceAll("\"", "")))
            .orElse(toSnakeCase(className));
    }

    private DataEntity.Field extractField(FieldDeclaration fieldDecl) {
        String fieldName = fieldDecl.getVariables().get(0).getNameAsString();
        String fieldType = fieldDecl.getElementType().asString();

        boolean isNullable = fieldDecl.getAnnotations().stream()
            .noneMatch(ann -> COLUMN_ANNOTATION.equals(ann.getNameAsString()) &&
                ann instanceof NormalAnnotationExpr &&
                ((NormalAnnotationExpr) ann).getPairs().stream()
                    .anyMatch(pair -> NULLABLE_ATTRIBUTE.equals(pair.getNameAsString()) &&
                        FALSE_LITERAL.equals(pair.getValue().toString())));

        return new DataEntity.Field(
            fieldName,
            fieldType,
            isNullable,
            null
        );
    }

    private boolean isIdField(FieldDeclaration fieldDecl) {
        return fieldDecl.getAnnotations().stream()
            .anyMatch(ann -> ID_ANNOTATION.equals(ann.getNameAsString()));
    }

    private void extractRelationship(FieldDeclaration fieldDecl, String sourceEntity,
                                     AnnotationExpr annotation, List<Relationship> relationships) {
        String fieldType = fieldDecl.getElementType().asString();
        String targetEntity = fieldType
            .replaceAll(LIST_TYPE_PATTERN, "$1")
            .replaceAll(SET_TYPE_PATTERN, "$1")
            .replaceAll(COLLECTION_TYPE_PATTERN, "$1");

        String annotationName = annotation.getNameAsString();
        String description = annotationName + RELATIONSHIP_DESCRIPTION_SUFFIX;

        Relationship relationship = new Relationship(
            sourceEntity,
            targetEntity,
            RelationshipType.DEPENDS_ON,  
            description,
            RELATIONSHIP_TECHNOLOGY
        );

        relationships.add(relationship);
        log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, annotationName, targetEntity);
    }

    protected String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
