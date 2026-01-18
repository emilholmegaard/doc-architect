package com.docarchitect.core.scanner.impl.php;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Doctrine ORM entity declarations in PHP source files.
 *
 * <p>Parses Doctrine entity definitions to extract database entities, fields,
 * and relationships. Supports both PHP 8 attributes and legacy Doctrine annotations.
 *
 * <p><b>Supported Entity Detection - PHP 8 Attributes</b></p>
 * <ul>
 *   <li>{@code #[Entity]} - Entity marker</li>
 *   <li>{@code #[Table(name: 'users')]} - Custom table name</li>
 *   <li>{@code #[Column(type: 'string', length: 255)]} - Column definition</li>
 *   <li>{@code #[Id]} - Primary key marker</li>
 *   <li>{@code #[GeneratedValue]} - Auto-increment</li>
 * </ul>
 *
 * <p><b>Supported Entity Detection - Doctrine Annotations</b></p>
 * <ul>
 *   <li>{@code @Entity} - Entity marker</li>
 *   <li>{@code @Table(name="users")} - Custom table name</li>
 *   <li>{@code @Column(type="string")} - Column definition</li>
 *   <li>{@code @Id} - Primary key marker</li>
 * </ul>
 *
 * <p><b>Relationship Detection</b></p>
 * <ul>
 *   <li>{@code #[OneToMany(targetEntity: Post::class)]} - One-to-many</li>
 *   <li>{@code #[ManyToOne(targetEntity: User::class)]} - Many-to-one</li>
 *   <li>{@code #[OneToOne(targetEntity: Profile::class)]} - One-to-one</li>
 *   <li>{@code #[ManyToMany(targetEntity: Tag::class)]} - Many-to-many</li>
 *   <li>{@code @OneToMany(targetEntity="Post")} - Legacy annotation style</li>
 * </ul>
 *
 * @see DataEntity
 * @see Relationship
 * @since 1.0.0
 */
public class DoctrineEntityScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "doctrine-entities";
    private static final String SCANNER_DISPLAY_NAME = "Doctrine Entity Scanner";
    private static final String PATTERN_PHP_FILES = "**/*.php";
    private static final String PATTERN_ENTITY_ROOT = "src/Entity/*.php";
    private static final String PATTERN_ENTITY_NESTED = "**/Entity/*.php";
    private static final String PATTERN_ENTITY_SUFFIX = "**/*Entity.php";

    private static final int SCANNER_PRIORITY = 60;

    /**
     * Regex to match PHP 8 Entity attribute or Doctrine annotation.
     * Matches: #[Entity] or @Entity or #[ORM\Entity]
     */
    private static final Pattern ENTITY_MARKER_PATTERN = Pattern.compile(
        "#\\[(?:ORM\\\\)?Entity\\]|@(?:ORM\\\\)?Entity\\b"
    );

    /**
     * Regex to match PHP class definition.
     * Captures: (1) class name
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "class\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+extends|\\s+implements|\\s*\\{)"
    );

    /**
     * Regex to extract PHP namespace.
     * Captures: (1) namespace
     */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
        "namespace\\s+([A-Za-z_][A-Za-z0-9_\\\\]*);",
        Pattern.MULTILINE
    );

    /**
     * Regex to extract table name from PHP 8 attribute.
     * Matches: #[Table(name: 'users')] or #[ORM\Table(name: 'users')]
     * Captures: (1) table name
     */
    private static final Pattern PHP8_TABLE_PATTERN = Pattern.compile(
        "#\\[(?:ORM\\\\)?Table\\s*\\(\\s*name\\s*:\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract table name from Doctrine annotation.
     * Matches: @Table(name="users") or @ORM\Table(name="users")
     * Captures: (1) table name
     */
    private static final Pattern ANNOTATION_TABLE_PATTERN = Pattern.compile(
        "@(?:ORM\\\\)?Table\\s*\\(\\s*name\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract PHP 8 Column attributes.
     * Matches: #[Column(type: 'string', length: 255)]
     * Captures: (1) column parameters, (2) property type (including ?nullable and \namespace), (3) property name
     */
    private static final Pattern PHP8_COLUMN_PATTERN = Pattern.compile(
        "#\\[(?:ORM\\\\)?Column\\s*\\(([^)]+)\\)\\s*\\]\\s*(?:private|protected|public)\\s+(\\??\\\\?[A-Za-z_][A-Za-z0-9_\\\\]*)\\s+\\$([A-Za-z_][A-Za-z0-9_]*)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract Doctrine Column annotations.
     * Matches: @Column(type="string") followed by property declaration
     * Captures: (1) column parameters, (2) property type (including ?nullable and \namespace), (3) property name
     */
    private static final Pattern ANNOTATION_COLUMN_PATTERN = Pattern.compile(
        "@(?:ORM\\\\)?Column\\s*\\(([^)]+)\\)[^$]*(?:private|protected|public)\\s+(\\??\\\\?[A-Za-z_][A-Za-z0-9_\\\\]*)\\s+\\$([A-Za-z_][A-Za-z0-9_]*)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract Id attribute/annotation.
     * Matches: #[Id] or @Id followed by property
     */
    private static final Pattern ID_PATTERN = Pattern.compile(
        "(?:#\\[(?:ORM\\\\)?Id\\]|@(?:ORM\\\\)?Id)[^$]*(?:private|protected|public)\\s+(?:\\?)?[A-Za-z_][A-Za-z0-9_]*\\s+\\$([A-Za-z_][A-Za-z0-9_]*)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract PHP 8 relationship attributes.
     * Matches: #[OneToMany(targetEntity: Post::class, mappedBy: 'author')]
     * Captures: (1) relationship type, (2) attribute content
     */
    private static final Pattern PHP8_RELATIONSHIP_PATTERN = Pattern.compile(
        "#\\[(?:ORM\\\\)?(OneToMany|ManyToOne|OneToOne|ManyToMany)\\s*\\(([^)]+)\\)\\s*\\]",
        Pattern.DOTALL
    );

    /**
     * Regex to extract Doctrine relationship annotations.
     * Matches: @OneToMany(targetEntity="Post", mappedBy="author")
     * Captures: (1) relationship type, (2) annotation content
     */
    private static final Pattern ANNOTATION_RELATIONSHIP_PATTERN = Pattern.compile(
        "@(?:ORM\\\\)?(OneToMany|ManyToOne|OneToOne|ManyToMany)\\s*\\(([^)]+)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract target entity from PHP 8 attribute.
     * Matches: targetEntity: Post::class
     * Captures: (1) target class name
     */
    private static final Pattern PHP8_TARGET_ENTITY_PATTERN = Pattern.compile(
        "targetEntity\\s*:\\s*([A-Za-z_][A-Za-z0-9_\\\\]*)::class"
    );

    /**
     * Regex to extract target entity from Doctrine annotation.
     * Matches: targetEntity="Post" or targetEntity="App\Entity\Post"
     * Captures: (1) target class name
     */
    private static final Pattern ANNOTATION_TARGET_ENTITY_PATTERN = Pattern.compile(
        "targetEntity\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract column type from attribute/annotation content.
     * Matches: type: 'string' or type="string"
     * Captures: (1) type value
     */
    private static final Pattern COLUMN_TYPE_PATTERN = Pattern.compile(
        "type\\s*[=:]\\s*['\"]([^'\"]+)['\"]"
    );

    private static final String DATA_ENTITY_TYPE = "table";
    private static final String DESCRIPTION_PREFIX = "Doctrine Entity: ";
    private static final String RELATIONSHIP_TECHNOLOGY = "Doctrine";
    private static final String DEFAULT_PRIMARY_KEY = "id";

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
        return Set.of(PATTERN_PHP_FILES, PATTERN_ENTITY_ROOT, PATTERN_ENTITY_NESTED, PATTERN_ENTITY_SUFFIX);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasPhpFiles()
            .and(ApplicabilityStrategies.hasDoctrine()
                .or(ApplicabilityStrategies.hasFileContaining(
                    "#[Entity]",
                    "@Entity",
                    "Doctrine\\ORM\\Mapping",
                    "ORM\\Entity"
                )));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Doctrine entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        // Scan entity directories (both root and nested patterns), using Set to deduplicate
        java.util.Set<Path> entityFiles = new java.util.LinkedHashSet<>();
        context.findFiles(PATTERN_ENTITY_ROOT).forEach(entityFiles::add);
        context.findFiles(PATTERN_ENTITY_NESTED).forEach(entityFiles::add);
        context.findFiles(PATTERN_ENTITY_SUFFIX).forEach(entityFiles::add);

        // Also scan all PHP files for entities outside standard locations
        List<Path> otherPhpFiles = context.findFiles(PATTERN_PHP_FILES)
            .filter(f -> !isEntityFile(f))
            .toList();

        for (Path entityFile : entityFiles) {
            try {
                parseEntityFile(entityFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse Doctrine entity: {} - {}", entityFile, e.getMessage());
            }
        }

        for (Path phpFile : otherPhpFiles) {
            try {
                if (shouldScanFile(phpFile)) {
                    parseEntityFile(phpFile, dataEntities, relationships);
                }
            } catch (Exception e) {
                log.debug("Failed to parse PHP file for Doctrine entities: {} - {}", phpFile, e.getMessage());
            }
        }

        log.info("Found {} Doctrine entities and {} relationships", dataEntities.size(), relationships.size());

        return buildSuccessResult(
            List.of(),           // No components
            List.of(),           // No dependencies
            List.of(),           // No API endpoints
            List.of(),           // No message flows
            dataEntities,        // Data entities
            relationships,       // Relationships
            List.of()            // No warnings
        );
    }

    /**
     * Checks if a file is in a standard Doctrine entity directory.
     */
    private boolean isEntityFile(Path file) {
        String pathStr = file.toString().replace('\\', '/');
        return pathStr.contains("/Entity/") || pathStr.endsWith("Entity.php");
    }

    /**
     * Pre-filters files to only scan those likely to contain Doctrine entities.
     */
    private boolean shouldScanFile(Path file) throws IOException {
        String content = readFileContent(file);
        return content.contains("#[Entity]") || content.contains("@Entity") ||
               content.contains("Doctrine\\ORM\\Mapping");
    }

    /**
     * Parses a PHP file for Doctrine entity definitions.
     */
    private void parseEntityFile(Path file, List<DataEntity> dataEntities, List<Relationship> relationships)
            throws IOException {
        String content = readFileContent(file);

        // Check if this file contains an Entity marker
        Matcher entityMatcher = ENTITY_MARKER_PATTERN.matcher(content);
        if (!entityMatcher.find()) {
            return; // Not a Doctrine entity
        }

        // Find class definition
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) {
            return; // No class found
        }

        String className = classMatcher.group(1);
        String namespace = extractNamespace(content);
        String componentId = namespace.isEmpty() ? className : namespace + "\\" + className;

        // Extract table name
        String tableName = extractTableName(content, className);

        // Extract primary key
        String primaryKey = extractPrimaryKey(content);

        // Extract fields
        List<DataEntity.Field> fields = extractFields(content);

        // Create data entity
        DataEntity entity = new DataEntity(
            componentId,
            tableName,
            DATA_ENTITY_TYPE,
            fields,
            primaryKey,
            DESCRIPTION_PREFIX + className
        );
        dataEntities.add(entity);

        // Extract relationships
        extractRelationships(content, componentId, namespace, relationships);

        log.debug("Found Doctrine entity: {} -> table: {}", componentId, tableName);
    }

    /**
     * Extracts the namespace from PHP file content.
     */
    private String extractNamespace(String content) {
        Matcher matcher = NAMESPACE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Extracts the table name from the entity or derives it from class name.
     */
    private String extractTableName(String content, String className) {
        // Try PHP 8 attribute first
        Matcher php8Matcher = PHP8_TABLE_PATTERN.matcher(content);
        if (php8Matcher.find()) {
            return php8Matcher.group(1);
        }

        // Try Doctrine annotation
        Matcher annotationMatcher = ANNOTATION_TABLE_PATTERN.matcher(content);
        if (annotationMatcher.find()) {
            return annotationMatcher.group(1);
        }

        // Default: snake_case of class name
        return toSnakeCase(className);
    }

    /**
     * Extracts the primary key field from the entity.
     */
    private String extractPrimaryKey(String content) {
        Matcher matcher = ID_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : DEFAULT_PRIMARY_KEY;
    }

    /**
     * Extracts fields from Column attributes/annotations.
     */
    private List<DataEntity.Field> extractFields(String content) {
        List<DataEntity.Field> fields = new ArrayList<>();
        java.util.Set<String> processedFields = new java.util.HashSet<>();

        // Extract PHP 8 Column attributes
        Matcher php8Matcher = PHP8_COLUMN_PATTERN.matcher(content);
        while (php8Matcher.find()) {
            String columnParams = php8Matcher.group(1);
            String phpType = php8Matcher.group(2);
            String fieldName = php8Matcher.group(3);

            if (!processedFields.contains(fieldName)) {
                String columnType = extractColumnType(columnParams, phpType);
                boolean nullable = phpType.startsWith("?") || columnParams.contains("nullable: true") ||
                                  columnParams.contains("nullable=true");
                fields.add(new DataEntity.Field(fieldName, columnType, nullable, null));
                processedFields.add(fieldName);
            }
        }

        // Extract Doctrine Column annotations
        Matcher annotationMatcher = ANNOTATION_COLUMN_PATTERN.matcher(content);
        while (annotationMatcher.find()) {
            String columnParams = annotationMatcher.group(1);
            String phpType = annotationMatcher.group(2);
            String fieldName = annotationMatcher.group(3);

            if (!processedFields.contains(fieldName)) {
                String columnType = extractColumnType(columnParams, phpType);
                boolean nullable = columnParams.contains("nullable=true") || columnParams.contains("nullable = true");
                fields.add(new DataEntity.Field(fieldName, columnType, nullable, null));
                processedFields.add(fieldName);
            }
        }

        return fields;
    }

    /**
     * Extracts column type from attribute/annotation parameters.
     */
    private String extractColumnType(String columnParams, String phpType) {
        Matcher typeMatcher = COLUMN_TYPE_PATTERN.matcher(columnParams);
        if (typeMatcher.find()) {
            return typeMatcher.group(1);
        }
        // Fall back to PHP type
        return phpType != null ? phpType.replace("?", "") : "mixed";
    }

    /**
     * Extracts relationships from the entity.
     */
    private void extractRelationships(String content, String sourceEntity, String namespace,
                                      List<Relationship> relationships) {
        // Extract PHP 8 relationship attributes
        Matcher php8Matcher = PHP8_RELATIONSHIP_PATTERN.matcher(content);
        while (php8Matcher.find()) {
            String relationshipType = php8Matcher.group(1);
            String attributeContent = php8Matcher.group(2);

            String targetEntity = extractTargetEntityPhp8(attributeContent, namespace);
            if (targetEntity != null) {
                addRelationship(sourceEntity, targetEntity, relationshipType, relationships);
            }
        }

        // Extract Doctrine annotation relationships
        Matcher annotationMatcher = ANNOTATION_RELATIONSHIP_PATTERN.matcher(content);
        while (annotationMatcher.find()) {
            String relationshipType = annotationMatcher.group(1);
            String annotationContent = annotationMatcher.group(2);

            String targetEntity = extractTargetEntityAnnotation(annotationContent, namespace);
            if (targetEntity != null) {
                addRelationship(sourceEntity, targetEntity, relationshipType, relationships);
            }
        }
    }

    /**
     * Extracts target entity from PHP 8 attribute content.
     */
    private String extractTargetEntityPhp8(String attributeContent, String namespace) {
        Matcher matcher = PHP8_TARGET_ENTITY_PATTERN.matcher(attributeContent);
        if (matcher.find()) {
            String target = matcher.group(1);
            if (!target.contains("\\")) {
                return namespace.isEmpty() ? target : namespace + "\\" + target;
            }
            return target;
        }
        return null;
    }

    /**
     * Extracts target entity from Doctrine annotation content.
     */
    private String extractTargetEntityAnnotation(String annotationContent, String namespace) {
        Matcher matcher = ANNOTATION_TARGET_ENTITY_PATTERN.matcher(annotationContent);
        if (matcher.find()) {
            String target = matcher.group(1);
            if (!target.contains("\\")) {
                return namespace.isEmpty() ? target : namespace + "\\" + target;
            }
            return target;
        }
        return null;
    }

    /**
     * Adds a relationship to the list.
     */
    private void addRelationship(String sourceEntity, String targetEntity, String relationshipType,
                                 List<Relationship> relationships) {
        String description = relationshipType + " to " + targetEntity;

        Relationship relationship = new Relationship(
            sourceEntity,
            targetEntity,
            RelationshipType.DEPENDS_ON,
            description,
            RELATIONSHIP_TECHNOLOGY
        );
        relationships.add(relationship);

        log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, relationshipType, targetEntity);
    }

    /**
     * Converts CamelCase to snake_case.
     * Example: UserProfile -> user_profile
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
