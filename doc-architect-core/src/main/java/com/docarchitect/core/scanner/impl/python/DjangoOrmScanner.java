package com.docarchitect.core.scanner.impl.python;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.PythonAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
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

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Django models in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> modelFiles = new ArrayList<>();
        context.findFiles(PATTERN_MODELS_PY).forEach(modelFiles::add);
        context.findFiles(PATTERN_MODELS_SUFFIX).forEach(modelFiles::add);

        if (modelFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path modelFile : modelFiles) {
            try {
                parsePythonFile(modelFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse Django models file: {} - {}", modelFile, e.getMessage());
            }
        }

        log.info("Found {} Django models and {} relationships", dataEntities.size(), relationships.size());

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
