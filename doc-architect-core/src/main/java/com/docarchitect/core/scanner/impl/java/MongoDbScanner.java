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
 * Scanner for Spring Data MongoDB document declarations in Java source files.
 *
 * <p>Detects MongoDB-specific patterns including:
 * <ul>
 *   <li>@Document annotated classes with collection names</li>
 *   <li>@Field annotations for field mapping</li>
 *   <li>@DBRef annotations for document references (creates relationships)</li>
 *   <li>Embedded documents (fields without @DBRef)</li>
 *   <li>@Id annotations for primary keys</li>
 * </ul>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * @Document(collection = "accounts")
 * public class Account {
 *     @Id
 *     private String id;
 *
 *     @Field("username")
 *     private String username;
 *
 *     @DBRef
 *     private User owner;  // → Creates Relationship
 *
 *     private List<Item> items;  // Embedded documents
 * }
 * }</pre>
 *
 * <p><b>Pre-filtering Strategy:</b>
 * Only scans files containing Spring Data MongoDB imports to optimize performance.
 *
 * @see com.docarchitect.core.scanner.Scanner
 * @see com.docarchitect.core.model.DataEntity
 * @since 1.0.0
 */
public class MongoDbScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "mongodb";
    private static final String SCANNER_DISPLAY_NAME = "MongoDB Scanner";
    private static final String JAVA_FILE_GLOB = "**/*.java";
    private static final int SCANNER_PRIORITY = 61;

    private static final String DOCUMENT_ANNOTATION = "Document";
    private static final String FIELD_ANNOTATION = "Field";
    private static final String DBREF_ANNOTATION = "DBRef";
    private static final String ID_ANNOTATION = "Id";
    private static final String COLLECTION_ATTRIBUTE = "collection";

    private static final String DATA_ENTITY_TYPE_COLLECTION = "collection";
    private static final String DOCUMENT_DESCRIPTION_PREFIX = "MongoDB Document: ";
    private static final String RELATIONSHIP_TECHNOLOGY = "MongoDB";
    private static final String DBREF_RELATIONSHIP_DESCRIPTION = "DBRef relationship";

    private static final String LIST_TYPE_PATTERN = "List<(.+)>";
    private static final String SET_TYPE_PATTERN = "Set<(.+)>";
    private static final String COLLECTION_TYPE_PATTERN = "Collection<(.+)>";

    private static final Set<String> MONGODB_IMPORTS = Set.of(
        "org.springframework.data.mongodb",
        "@Document",
        "@Field",
        "@DBRef"
    );

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final Pattern DOCUMENT_PATTERN =
        Pattern.compile("@Document\\s*\\(\\s*collection\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern FIELD_PATTERN =
        Pattern.compile("@Field\\s*\\(\\s*[\"']([^\"']+)[\"']");

    private static final Pattern DBREF_PATTERN =
        Pattern.compile("@DBRef[\\s\\n]+(?:private|public|protected)?\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)");

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
            .and(ApplicabilityStrategies.hasMongoDB()
                .or(ApplicabilityStrategies.hasFileContaining("org.springframework.data.mongodb", "@Document", "@DBRef")));
    }

    @Override
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);
            // Pre-filter: Only scan files with Spring Data MongoDB imports
            return MONGODB_IMPORTS.stream().anyMatch(content::contains);
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning MongoDB documents in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> javaFiles = context.findFiles(JAVA_FILE_GLOB).toList();
        statsBuilder.filesDiscovered(javaFiles.size());

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        int skippedFiles = 0;

        for (Path javaFile : javaFiles) {
            if (!shouldScanFile(javaFile)) {
                skippedFiles++;
                continue;
            }

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

        log.debug("Pre-filtered {} files (not MongoDB documents)", skippedFiles);

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} MongoDB documents and {} relationships (success rate: {:.1f}%, overall parse rate: {:.1f}%)",
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
     * Internal record to hold entity and its relationships together.
     */
    private record EntityResult(DataEntity entity, List<Relationship> relationships) {}

    /**
     * Extracts entities from a parsed CompilationUnit using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param cu the parsed CompilationUnit
     * @return list of entity results (entity + relationships)
     */
    private List<EntityResult> extractEntitiesFromAST(CompilationUnit cu) {
        List<EntityResult> results = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (!hasAnnotation(classDecl, DOCUMENT_ANNOTATION)) {
                return;
            }

            String className = classDecl.getNameAsString();
            String packageName = getPackageName(cu);
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            String collectionName = extractCollectionName(classDecl, className);
            String description = DOCUMENT_DESCRIPTION_PREFIX + className;

            List<DataEntity.Field> fields = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            String primaryKey = null;

            for (FieldDeclaration fieldDecl : classDecl.getFields()) {
                // Check for @DBRef relationship
                if (hasAnnotation(fieldDecl, DBREF_ANNOTATION)) {
                    extractDbRefRelationship(fieldDecl, fullyQualifiedName, relationships);
                } else {
                    // Regular field or embedded document
                    DataEntity.Field field = extractField(fieldDecl);
                    if (field != null) {
                        fields.add(field);
                        if (hasAnnotation(fieldDecl, ID_ANNOTATION) && primaryKey == null) {
                            primaryKey = field.name();
                        }
                    }
                }
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                collectionName,
                DATA_ENTITY_TYPE_COLLECTION,
                fields,
                primaryKey,
                description
            );

            results.add(new EntityResult(entity, relationships));
            log.debug("Found MongoDB document: {} -> collection: {}", fullyQualifiedName, collectionName);
        });

        return results;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * <p>The fallback strategy uses regex patterns to extract:
     * <ul>
     *   <li>@Document annotations with collection names</li>
     *   <li>Basic field information</li>
     *   <li>@DBRef relationships (simplified)</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<EntityResult> createFallbackStrategy() {
        return (file, content) -> {
            List<EntityResult> results = new ArrayList<>();

            // Check if file contains MongoDB document annotations
            if (!content.contains("@Document")) {
                return results;
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract collection name using pattern
            String collectionName = className; // Default
            Matcher collectionMatcher = DOCUMENT_PATTERN.matcher(content);
            if (collectionMatcher.find()) {
                collectionName = collectionMatcher.group(1);
            } else {
                collectionName = toSnakeCase(className);
            }

            // Extract basic fields using shared field pattern
            List<DataEntity.Field> fields = new ArrayList<>();
            Matcher fieldMatcher = RegexPatterns.FIELD_PATTERN.matcher(content);
            while (fieldMatcher.find()) {
                String fieldType = fieldMatcher.group(1);
                String fieldName = fieldMatcher.group(2);

                // Skip fields that look like relationships
                if (!fieldType.startsWith("List<") && !fieldType.startsWith("Set<") &&
                    !fieldType.startsWith("Collection<")) {
                    fields.add(new DataEntity.Field(fieldName, fieldType, true, null));
                }
            }

            // Extract @DBRef relationships (simplified)
            List<Relationship> relationships = new ArrayList<>();
            Matcher dbrefMatcher = DBREF_PATTERN.matcher(content);
            while (dbrefMatcher.find()) {
                String targetType = dbrefMatcher.group(1);
                // Extract target entity type (handle collections)
                String targetEntity = targetType
                    .replaceAll(LIST_TYPE_PATTERN, "$1")
                    .replaceAll(SET_TYPE_PATTERN, "$1")
                    .replaceAll(COLLECTION_TYPE_PATTERN, "$1");

                relationships.add(new Relationship(
                    fullyQualifiedName,
                    targetEntity,
                    RelationshipType.DEPENDS_ON,
                    DBREF_RELATIONSHIP_DESCRIPTION,
                    RELATIONSHIP_TECHNOLOGY
                ));
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                collectionName,
                DATA_ENTITY_TYPE_COLLECTION,
                fields,
                null, // primary key unknown in fallback
                DOCUMENT_DESCRIPTION_PREFIX + className
            );

            results.add(new EntityResult(entity, relationships));
            log.debug("Fallback parsing found entity: {} -> {}", fullyQualifiedName, collectionName);
            return results;
        };
    }

    /**
     * @deprecated Use {@link #extractEntitiesFromAST(CompilationUnit)} instead
     */
    @Deprecated
    private void parseMongoDocuments(Path javaFile, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
        if (cuOpt.isEmpty()) {
            return;
        }

        CompilationUnit cu = cuOpt.get();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (!hasAnnotation(classDecl, DOCUMENT_ANNOTATION)) {
                return;
            }

            String className = classDecl.getNameAsString();
            String packageName = getPackageName(cu);
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            String collectionName = extractCollectionName(classDecl, className);
            String description = DOCUMENT_DESCRIPTION_PREFIX + className;

            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            for (FieldDeclaration fieldDecl : classDecl.getFields()) {
                // Check for @DBRef relationship
                if (hasAnnotation(fieldDecl, DBREF_ANNOTATION)) {
                    extractDbRefRelationship(fieldDecl, fullyQualifiedName, relationships);
                } else {
                    // Regular field or embedded document
                    DataEntity.Field field = extractField(fieldDecl);
                    if (field != null) {
                        fields.add(field);
                        if (hasAnnotation(fieldDecl, ID_ANNOTATION) && primaryKey == null) {
                            primaryKey = field.name();
                        }
                    }
                }
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                collectionName,
                DATA_ENTITY_TYPE_COLLECTION,
                fields,
                primaryKey,
                description
            );

            dataEntities.add(entity);
            log.debug("Found MongoDB document: {} -> collection: {}", fullyQualifiedName, collectionName);
        });
    }

    /**
     * Extracts collection name from @Document annotation or derives from class name.
     *
     * <p>Examples:
     * <ul>
     *   <li>@Document(collection = "accounts") → "accounts"</li>
     *   <li>@Document → "user_account" (snake_case of UserAccount)</li>
     * </ul>
     *
     * @param classDecl class declaration
     * @param className class name
     * @return collection name
     */
    private String extractCollectionName(ClassOrInterfaceDeclaration classDecl, String className) {
        return classDecl.getAnnotations().stream()
            .filter(ann -> DOCUMENT_ANNOTATION.equals(ann.getNameAsString()))
            .filter(ann -> ann instanceof NormalAnnotationExpr)
            .findFirst()
            .flatMap(ann -> ((NormalAnnotationExpr) ann).getPairs().stream()
                .filter(pair -> COLLECTION_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> cleanStringLiteral(pair.getValue().toString())))
            .orElse(toSnakeCase(className));
    }

    /**
     * Extracts field information including @Field annotation mapping.
     *
     * <p>Handles:
     * <ul>
     *   <li>@Field("field_name") → uses mapped name</li>
     *   <li>@Field → uses Java field name</li>
     *   <li>No annotation → uses Java field name</li>
     * </ul>
     *
     * @param fieldDecl field declaration
     * @return field metadata
     */
    private DataEntity.Field extractField(FieldDeclaration fieldDecl) {
        String javaFieldName = fieldDecl.getVariables().get(0).getNameAsString();
        String fieldType = fieldDecl.getElementType().asString();

        // Check for @Field annotation with custom name
        String mappedFieldName = getAnnotationAttribute(fieldDecl, FIELD_ANNOTATION, "value");
        String fieldName = (mappedFieldName != null && !mappedFieldName.isEmpty())
            ? mappedFieldName
            : javaFieldName;

        // MongoDB fields are generally nullable unless explicitly validated
        boolean isNullable = true;

        return new DataEntity.Field(
            fieldName,
            fieldType,
            isNullable,
            null
        );
    }

    /**
     * Extracts @DBRef relationships and creates Relationship records.
     *
     * <p>Handles:
     * <ul>
     *   <li>Single references: @DBRef private User owner;</li>
     *   <li>Collection references: @DBRef private List&lt;Item&gt; items;</li>
     * </ul>
     *
     * @param fieldDecl field declaration
     * @param sourceEntity source entity FQN
     * @param relationships list to append relationships to
     */
    private void extractDbRefRelationship(FieldDeclaration fieldDecl, String sourceEntity,
                                          List<Relationship> relationships) {
        String fieldType = fieldDecl.getElementType().asString();

        // Extract target entity type (handle collections)
        String targetEntity = fieldType
            .replaceAll(LIST_TYPE_PATTERN, "$1")
            .replaceAll(SET_TYPE_PATTERN, "$1")
            .replaceAll(COLLECTION_TYPE_PATTERN, "$1");

        Relationship relationship = new Relationship(
            sourceEntity,
            targetEntity,
            RelationshipType.DEPENDS_ON,
            DBREF_RELATIONSHIP_DESCRIPTION,
            RELATIONSHIP_TECHNOLOGY
        );

        relationships.add(relationship);
        log.debug("Found @DBRef relationship: {} --[DBRef]--> {}", sourceEntity, targetEntity);
    }
}
