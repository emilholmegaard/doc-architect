package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.PythonAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Django ORM model definitions in Python source files.
 *
 * <p>Uses AST parsing via {@link PythonAst} to extract Django model classes,
 * fields, and relationships from Python source code.
 *
 * <p><b>Supported Patterns</b></p>
 *
 * <p><b>Django Model Classes:</b></p>
 * <pre>{@code
 * class User(models.Model):
 *     id = models.AutoField(primary_key=True)
 *     username = models.CharField(max_length=100, unique=True)
 *     email = models.EmailField(max_length=255)
 *     is_active = models.BooleanField(default=True)
 *     created_at = models.DateTimeField(auto_now_add=True)
 * }</pre>
 *
 * <p><b>Field Types</b></p>
 * <ul>
 *   <li>{@code models.CharField} - String field</li>
 *   <li>{@code models.IntegerField} - Integer field</li>
 *   <li>{@code models.BooleanField} - Boolean field</li>
 *   <li>{@code models.DateTimeField} - DateTime field</li>
 *   <li>{@code models.ForeignKey} - Foreign key relationship</li>
 *   <li>{@code models.ManyToManyField} - Many-to-many relationship</li>
 *   <li>{@code models.OneToOneField} - One-to-one relationship</li>
 * </ul>
 *
 * @see PythonAst
 * @see DataEntity
 * @since 1.0.0
 */
public class DjangoOrmScanner extends AbstractAstScanner<PythonAst.PythonClass> {

    private static final String SCANNER_ID = "django-orm";
    private static final String SCANNER_DISPLAY_NAME = "Django ORM Scanner";
    private static final String PATTERN_MODELS_PY = "**/models.py";
    private static final String PATTERN_MODELS_SUFFIX = "**/*_models.py";
    private static final String DJANGO_SOURCE = "Django";
    
    private static final String BASE_CLASS_MODELS_MODEL = "models.Model";
    private static final String BASE_CLASS_MODEL = "Model";
    private static final String META_CLASS_NAME = "Meta";
    private static final String DEFAULT_PRIMARY_KEY = "id";
    private static final String ENTITY_TYPE_TABLE = "table";
    
    private static final String FIELD_FOREIGN_KEY = "ForeignKey";
    private static final String FIELD_ONE_TO_ONE = "OneToOneField";
    private static final String FIELD_MANY_TO_MANY = "ManyToManyField";
    
    private static final String FIELD_CHAR = "CharField";
    private static final String FIELD_TEXT = "TextField";
    private static final String FIELD_EMAIL = "EmailField";
    private static final String FIELD_URL = "URLField";
    private static final String FIELD_INTEGER = "IntegerField";
    private static final String FIELD_SMALL_INTEGER = "SmallIntegerField";
    private static final String FIELD_POSITIVE_INTEGER = "PositiveIntegerField";
    private static final String FIELD_BIG_INTEGER = "BigIntegerField";
    private static final String FIELD_BOOLEAN = "BooleanField";
    private static final String FIELD_DATETIME = "DateTimeField";
    private static final String FIELD_DATE = "DateField";
    private static final String FIELD_TIME = "TimeField";
    private static final String FIELD_DECIMAL = "DecimalField";
    private static final String FIELD_FLOAT = "FloatField";
    private static final String FIELD_BINARY = "BinaryField";
    private static final String FIELD_JSON = "JSONField";
    private static final String FIELD_UUID = "UUIDField";
    private static final String FIELD_AUTO = "AutoField";
    private static final String FIELD_BIG_AUTO = "BigAutoField";
    
    private static final String SQL_VARCHAR = "VARCHAR";
    private static final String SQL_INTEGER = "INTEGER";
    private static final String SQL_BIGINT = "BIGINT";
    private static final String SQL_BOOLEAN = "BOOLEAN";
    private static final String SQL_DATETIME = "DATETIME";
    private static final String SQL_DATE = "DATE";
    private static final String SQL_TIME = "TIME";
    private static final String SQL_DECIMAL = "DECIMAL";
    private static final String SQL_FLOAT = "FLOAT";
    private static final String SQL_BLOB = "BLOB";
    private static final String SQL_JSON = "JSON";
    private static final String SQL_UUID = "UUID";
    private static final String SQL_SERIAL = "SERIAL";
    
    private static final String PARAM_NULL_TRUE = "null=True";
    private static final String PARAM_BLANK_TRUE = "blank=True";
    private static final String PARAM_PRIMARY_KEY_TRUE = "primary_key=True";
    
    private static final String REGEX_CAMEL_TO_SNAKE = "([a-z])([A-Z])";
    private static final String REGEX_WORD_DOT = "[\\w.]+";

    private static final Set<String> RELATIONSHIP_FIELDS = Set.of(
        FIELD_FOREIGN_KEY, FIELD_ONE_TO_ONE, FIELD_MANY_TO_MANY
    );

    public DjangoOrmScanner() {
        super(AstParserFactory.getPythonParser());
    }

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
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_MODELS_PY, PATTERN_MODELS_SUFFIX);
    }

    @Override
    public int getPriority() {
        return 61;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PATTERN_MODELS_PY, PATTERN_MODELS_SUFFIX);
    }

    /**
     * Pre-filters files to avoid parsing SQLAlchemy or other non-Django Python files.
     *
     * <p>This method checks file content for framework-specific imports before
     * attempting AST parsing, preventing {@link ArrayIndexOutOfBoundsException}
     * and other parser errors when encountering incompatible patterns.
     *
     * <p><b>Detection Logic:</b></p>
     * <ul>
     *   <li><b>Skip:</b> SQLAlchemy files ({@code from sqlalchemy import})</li>
     *   <li><b>Accept:</b> Django imports ({@code from django.db import models})</li>
     *   <li><b>Accept:</b> Django model inheritance ({@code models.Model})</li>
     *   <li><b>Skip:</b> Everything else (no matching imports)</li>
     * </ul>
     *
     * @param file Python file to check
     * @return true if file likely contains Django models, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);

            // Skip SQLAlchemy files
            if (content.contains("from sqlalchemy import") ||
                content.contains("from sqlalchemy.") ||
                content.contains("declarative_base()") ||
                content.contains("from sqlmodel import")) {
                log.debug("Skipping SQLAlchemy file: {}", file.getFileName());
                return false;
            }

            // Accept Django files
            if (content.contains("from django.db import models") ||
                content.contains("from django.db import") ||
                content.contains("django.db.models") ||
                content.contains("models.Model")) {
                return true;
            }

            // Skip files without Django imports
            log.debug("Skipping non-Django file: {}", file.getFileName());
            return false;

        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {} - {}", file, e.getMessage());
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Django models in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> modelFiles = new ArrayList<>();
        context.findFiles(PATTERN_MODELS_PY).forEach(modelFiles::add);
        context.findFiles(PATTERN_MODELS_SUFFIX).forEach(modelFiles::add);

        statsBuilder.filesDiscovered(modelFiles.size());

        if (modelFiles.isEmpty()) {
            return emptyResult();
        }

        int skippedFiles = 0;

        for (Path modelFile : modelFiles) {
            if (!shouldScanFile(modelFile)) {
                skippedFiles++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<EntityResult> result = parseWithFallback(
                modelFile,
                classes -> extractEntitiesFromAST(classes),
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
        log.info("Found {} Django models and {} relationships (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
                dataEntities.size(), relationships.size(), statistics.getSuccessRate(), statistics.getOverallParseRate(), skippedFiles);

        return buildSuccessResult(
            List.of(),           // No components
            List.of(),           // No dependencies
            List.of(),           // No API endpoints
            List.of(),           // No message flows
            dataEntities,        // Data entities
            relationships,       // Relationships
            List.of(),           // No warnings
            statistics
        );
    }

    /**
     * Internal record to hold entity and its relationships together.
     */
    private record EntityResult(DataEntity entity, List<Relationship> relationships) {}

    /**
     * Extracts entities from parsed AST classes using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param classes the parsed Python classes
     * @return list of entity results (entity + relationships)
     */
    private List<EntityResult> extractEntitiesFromAST(List<PythonAst.PythonClass> classes) {
        List<EntityResult> results = new ArrayList<>();

        for (PythonAst.PythonClass pythonClass : classes) {
            // Skip non-Django models
            if (!pythonClass.inheritsFrom(BASE_CLASS_MODELS_MODEL) &&
                !pythonClass.inheritsFrom(BASE_CLASS_MODEL)) {
                continue;
            }

            // Extract table name
            String tableName = extractTableName(pythonClass);

            // Extract fields and relationships
            List<DataEntity.Field> fields = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            String primaryKey = null;

            for (PythonAst.Field field : pythonClass.fields()) {
                if (field.name().equals(META_CLASS_NAME) || field.name().startsWith("_")) {
                    continue;
                }

                // Check if this is a relationship field
                if (RELATIONSHIP_FIELDS.stream().anyMatch(rel -> field.type().contains(rel))) {
                    // Extract relationship
                    Relationship rel = extractRelationship(pythonClass.name(), field);
                    if (rel != null) {
                        relationships.add(rel);
                        log.debug("Found Django relationship: {} --[{}]--> {}",
                            pythonClass.name(), field.type(), rel.targetId());
                    }
                } else {
                    // Regular field
                    String sqlType = mapDjangoFieldToSql(field.type());
                    boolean nullable = isNullableField(field.value());
                    boolean isPrimaryKey = isPrimaryKeyField(field.value());

                    DataEntity.Field dataField = new DataEntity.Field(
                        field.name(),
                        sqlType,
                        nullable,
                        null
                    );

                    fields.add(dataField);

                    if (isPrimaryKey && primaryKey == null) {
                        primaryKey = field.name();
                    }

                    log.debug("Found Django field: {}.{} ({})", pythonClass.name(), field.name(), sqlType);
                }
            }

            // Django models always have an implicit 'id' primary key if not specified
            if (primaryKey == null) {
                primaryKey = DEFAULT_PRIMARY_KEY;
                DataEntity.Field idField = new DataEntity.Field(
                    DEFAULT_PRIMARY_KEY,
                    FIELD_AUTO,
                    false,
                    null
                );
                fields.add(0, idField);
            }

            // Create DataEntity
            if (!fields.isEmpty()) {
                DataEntity entity = new DataEntity(
                    pythonClass.name(),
                    tableName,
                    ENTITY_TYPE_TABLE,
                    fields,
                    primaryKey,
                    "Django Model: " + pythonClass.name()
                );

                results.add(new EntityResult(entity, relationships));
                log.debug("Found Django model: {} -> table: {}", pythonClass.name(), tableName);
            }
        }

        return results;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<EntityResult> createFallbackStrategy() {
        return (file, content) -> {
            List<EntityResult> results = new ArrayList<>();

            // Check if file contains Django patterns
            if (!content.contains("class ") || !content.contains("models.")) {
                return results;
            }

            // Simple regex-based extraction for basic Django models
            java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
                "class\\s+(\\w+)\\s*\\([^)]*models\\.Model[^)]*\\):"
            );
            java.util.regex.Matcher classMatcher = classPattern.matcher(content);

            while (classMatcher.find()) {
                String className = classMatcher.group(1);
                String tableName = toSnakeCase(className);

                // Extract simple fields using regex
                List<DataEntity.Field> fields = new ArrayList<>();

                // Add default id field
                fields.add(new DataEntity.Field(DEFAULT_PRIMARY_KEY, FIELD_AUTO, false, null));

                // Simple field pattern: field_name = models.SomeField(...)
                java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile(
                    "(\\w+)\\s*=\\s*models\\.(\\w+Field)\\("
                );
                java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(content);
                while (fieldMatcher.find()) {
                    String fieldName = fieldMatcher.group(1);
                    String fieldType = fieldMatcher.group(2);
                    if (!fieldName.startsWith("_")) {
                        String sqlType = mapDjangoFieldToSql(fieldType);
                        fields.add(new DataEntity.Field(fieldName, sqlType, true, null));
                    }
                }

                if (!fields.isEmpty()) {
                    DataEntity entity = new DataEntity(
                        className,
                        tableName,
                        ENTITY_TYPE_TABLE,
                        fields,
                        DEFAULT_PRIMARY_KEY,
                        "Django Model: " + className
                    );
                    results.add(new EntityResult(entity, List.of()));
                    log.debug("Fallback parsing found entity: {} -> {}", className, tableName);
                }
            }

            return results;
        };
    }

    /**
     * @deprecated Use {@link #extractEntitiesFromAST(List)} instead
     */
    @Deprecated
    private void parsePythonFile(Path file, List<DataEntity> dataEntities,
                                 List<Relationship> relationships) {
        List<PythonAst.PythonClass> classes = parseAstFile(file);

        for (PythonAst.PythonClass pythonClass : classes) {
            // Skip non-Django models
            if (!pythonClass.inheritsFrom(BASE_CLASS_MODELS_MODEL) &&
                !pythonClass.inheritsFrom(BASE_CLASS_MODEL)) {
                continue;
            }

            // Extract table name
            String tableName = extractTableName(pythonClass);

            // Extract fields and relationships
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            for (PythonAst.Field field : pythonClass.fields()) {
                if (field.name().equals(META_CLASS_NAME) || field.name().startsWith("_")) {
                    continue;
                }

                // Check if this is a relationship field
                if (RELATIONSHIP_FIELDS.stream().anyMatch(rel -> field.type().contains(rel))) {
                    // Extract relationship
                    Relationship rel = extractRelationship(pythonClass.name(), field);
                    if (rel != null) {
                        relationships.add(rel);
                        log.debug("Found Django relationship: {} --[{}]--> {}",
                            pythonClass.name(), field.type(), rel.targetId());
                    }
                } else {
                    // Regular field
                    String sqlType = mapDjangoFieldToSql(field.type());
                    boolean nullable = isNullableField(field.value());
                    boolean isPrimaryKey = isPrimaryKeyField(field.value());

                    DataEntity.Field dataField = new DataEntity.Field(
                        field.name(),
                        sqlType,
                        nullable,
                        null
                    );

                    fields.add(dataField);

                    if (isPrimaryKey && primaryKey == null) {
                        primaryKey = field.name();
                    }

                    log.debug("Found Django field: {}.{} ({})", pythonClass.name(), field.name(), sqlType);
                }
            }

            // Django models always have an implicit 'id' primary key if not specified
            if (primaryKey == null) {
                primaryKey = DEFAULT_PRIMARY_KEY;
                DataEntity.Field idField = new DataEntity.Field(
                    DEFAULT_PRIMARY_KEY,
                    FIELD_AUTO,
                    false,
                    null
                );
                fields.add(0, idField);
            }

            // Create DataEntity
            if (!fields.isEmpty()) {
                DataEntity entity = new DataEntity(
                    pythonClass.name(),
                    tableName,
                    ENTITY_TYPE_TABLE,
                    fields,
                    primaryKey,
                    "Django Model: " + pythonClass.name()
                );

                dataEntities.add(entity);
                log.debug("Found Django model: {} -> table: {}", pythonClass.name(), tableName);
            }
        }
    }

    /**
     * Extract table name from Django model class name.
     */
    private String extractTableName(PythonAst.PythonClass pythonClass) {
        // TODO: Could parse Meta class for db_table specification
        // For now, use Django convention: app_modelname
        return toSnakeCase(pythonClass.name());
    }

    /**
     * Extract relationship information from a field.
     */
    private Relationship extractRelationship(String sourceModel, PythonAst.Field field) {
        // Extract target model from field value
        // Example: models.ForeignKey('User', on_delete=models.CASCADE)
        if (field.value() == null) {
            return null;
        }

        String targetModel = extractTargetModel(field.value());
        if (targetModel == null) {
            return null;
        }

        RelationshipType relType = switch (field.type()) {
            case String s when s.contains(FIELD_FOREIGN_KEY) -> RelationshipType.DEPENDS_ON;
            case String s when s.contains(FIELD_ONE_TO_ONE) -> RelationshipType.DEPENDS_ON;
            case String s when s.contains(FIELD_MANY_TO_MANY) -> RelationshipType.DEPENDS_ON;
            default -> RelationshipType.DEPENDS_ON;
        };

        return new Relationship(
            sourceModel,
            targetModel,
            relType,
            field.type() + " relationship",
            DJANGO_SOURCE
        );
    }

    /**
     * Extract target model name from relationship field value.
     * Examples: 'User', "Profile", 'app.User'
     */
    private String extractTargetModel(String fieldValue) {
        // Find quoted string after ForeignKey/OneToOneField/ManyToManyField
        String[] parts = fieldValue.split("['\"]");
        for (int i = 1; i < parts.length; i += 2) {
            String part = parts[i].trim();
            if (!part.isEmpty() && part.matches(REGEX_WORD_DOT)) {
                // Extract just the class name if it's app.Model format
                if (part.contains(".")) {
                    return part.substring(part.lastIndexOf(".") + 1);
                }
                return part;
            }
        }
        return null;
    }

    /**
     * Maps Django field types to SQL types.
     */
    private String mapDjangoFieldToSql(String djangoField) {
        if (djangoField.contains(FIELD_CHAR) || djangoField.contains(FIELD_TEXT) ||
            djangoField.contains(FIELD_EMAIL) || djangoField.contains(FIELD_URL)) {
            return SQL_VARCHAR;
        } else if (djangoField.contains(FIELD_INTEGER) || djangoField.contains(FIELD_SMALL_INTEGER) ||
                   djangoField.contains(FIELD_POSITIVE_INTEGER)) {
            return SQL_INTEGER;
        } else if (djangoField.contains(FIELD_BIG_INTEGER)) {
            return SQL_BIGINT;
        } else if (djangoField.contains(FIELD_BOOLEAN)) {
            return SQL_BOOLEAN;
        } else if (djangoField.contains(FIELD_DATETIME)) {
            return SQL_DATETIME;
        } else if (djangoField.contains(FIELD_DATE)) {
            return SQL_DATE;
        } else if (djangoField.contains(FIELD_TIME)) {
            return SQL_TIME;
        } else if (djangoField.contains(FIELD_DECIMAL)) {
            return SQL_DECIMAL;
        } else if (djangoField.contains(FIELD_FLOAT)) {
            return SQL_FLOAT;
        } else if (djangoField.contains(FIELD_BINARY)) {
            return SQL_BLOB;
        } else if (djangoField.contains(FIELD_JSON)) {
            return SQL_JSON;
        } else if (djangoField.contains(FIELD_UUID)) {
            return SQL_UUID;
        } else if (djangoField.contains(FIELD_AUTO) || djangoField.contains(FIELD_BIG_AUTO)) {
            return SQL_SERIAL;
        }

        return djangoField;
    }

    /**
     * Checks if the field is nullable based on its value.
     */
    private boolean isNullableField(String fieldValue) {
        if (fieldValue == null) {
            return false;
        }

        if (fieldValue.contains(PARAM_NULL_TRUE)) {
            return true;
        }

        if (fieldValue.contains(PARAM_BLANK_TRUE)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the field is a primary key.
     */
    private boolean isPrimaryKeyField(String fieldValue) {
        return fieldValue != null && fieldValue.contains(PARAM_PRIMARY_KEY_TRUE);
    }

    /**
     * Converts CamelCase to snake_case.
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll(REGEX_CAMEL_TO_SNAKE, "$1_$2").toLowerCase();
    }
}
