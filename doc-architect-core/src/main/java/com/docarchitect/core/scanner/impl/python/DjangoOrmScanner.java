package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Django ORM model definitions in Python source files.
 *
 * <p>Since we're running in Java, we parse Python files as TEXT using regex patterns
 * to extract Django model classes, fields, and relationships.
 *
 * <h3>Supported Patterns</h3>
 *
 * <h4>Django Model Classes</h4>
 * <pre>{@code
 * class User(models.Model):
 *     id = models.AutoField(primary_key=True)
 *     username = models.CharField(max_length=100, unique=True)
 *     email = models.EmailField(max_length=255)
 *     is_active = models.BooleanField(default=True)
 *     created_at = models.DateTimeField(auto_now_add=True)
 * }</pre>
 *
 * <h3>Field Types</h3>
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
 * <h3>Relationship Patterns</h3>
 * <ul>
 *   <li>{@code models.ForeignKey("User", on_delete=models.CASCADE)}</li>
 *   <li>{@code models.ManyToManyField("Tag", related_name="posts")}</li>
 *   <li>{@code models.OneToOneField("Profile", on_delete=models.CASCADE)}</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code CLASS_PATTERN}: {@code class\s+(\w+)\s*\(.*models\.Model.*\):}</li>
 *   <li>{@code FIELD_PATTERN}: {@code (\w+)\s*=\s*models\.(\w+)\s*\((.+?)\)}</li>
 *   <li>{@code FOREIGNKEY_PATTERN}: {@code (\w+)\s*=\s*models\.(ForeignKey|OneToOneField|ManyToManyField)\s*\(\s*['"](.+?)['"]}</li>
 *   <li>{@code NULL_PATTERN}: {@code null\s*=\s*(True|False)}</li>
 *   <li>{@code PRIMARY_KEY_PATTERN}: {@code primary_key\s*=\s*True}</li>
 * </ul>
 *
 * @see DataEntity
 * @since 1.0.0
 */
public class DjangoOrmScanner extends AbstractRegexScanner {

    /**
     * Regex to match Django model class: class User(models.Model):.
     * Captures: (1) class name.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "class\\s+(\\w+)\\s*\\(.*models\\.Model.*\\):"
    );

    /**
     * Regex to match Django field: username = models.CharField(max_length=100).
     * Captures: (1) field name, (2) field type, (3) field arguments.
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "(\\w+)\\s*=\\s*models\\.(\\w+)\\s*\\((.+?)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to match relationship fields: user = models.ForeignKey("User", ...).
     * Captures: (1) field name, (2) relationship type, (3) target model.
     */
    private static final Pattern FOREIGNKEY_PATTERN = Pattern.compile(
        "(\\w+)\\s*=\\s*models\\.(ForeignKey|OneToOneField|ManyToManyField)\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex to extract null=True/False from field definition.
     */
    private static final Pattern NULL_PATTERN = Pattern.compile(
        "null\\s*=\\s*(True|False)"
    );

    /**
     * Regex to extract primary_key=True from field definition.
     */
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(
        "primary_key\\s*=\\s*True"
    );

    /**
     * Regex to extract blank=True/False from field definition.
     */
    private static final Pattern BLANK_PATTERN = Pattern.compile(
        "blank\\s*=\\s*(True|False)"
    );

    private static final Set<String> RELATIONSHIP_FIELDS = Set.of(
        "ForeignKey", "OneToOneField", "ManyToManyField"
    );

    @Override
    public String getId() {
        return "django-orm";
    }

    @Override
    public String getDisplayName() {
        return "Django ORM Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/models.py", "**/*_models.py");
    }

    @Override
    public int getPriority() {
        return 61;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/models.py", "**/*_models.py");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Django models in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> modelFiles = new ArrayList<>();
        context.findFiles("**/models.py").forEach(modelFiles::add);
        context.findFiles("**/*_models.py").forEach(modelFiles::add);

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

    private void parsePythonFile(Path file, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        String content = readFileContent(file);
        List<String> lines = readFileLines(file);

        // Find all Django model classes
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int classStartPos = classMatcher.start();

            // Extract class body
            String classBody = extractClassBody(lines, classStartPos, content);

            // Extract table name (default to snake_case of class name)
            String tableName = extractTableName(classBody, className);

            // Extract fields
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            Matcher fieldMatcher = FIELD_PATTERN.matcher(classBody);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String fieldType = fieldMatcher.group(2);
                String fieldArgs = fieldMatcher.group(3);

                // Skip Meta class and other special fields
                if (fieldName.equals("Meta") || fieldName.startsWith("_")) {
                    continue;
                }

                // Check if this is a relationship field
                if (RELATIONSHIP_FIELDS.contains(fieldType)) {
                    // Extract relationship
                    Matcher fkMatcher = FOREIGNKEY_PATTERN.matcher(classBody);
                    while (fkMatcher.find()) {
                        if (fkMatcher.group(1).equals(fieldName)) {
                            String targetModel = fkMatcher.group(3);

                            RelationshipType relType = switch (fieldType) {
                                case "ForeignKey" -> RelationshipType.DEPENDS_ON;
                                case "OneToOneField" -> RelationshipType.DEPENDS_ON;
                                case "ManyToManyField" -> RelationshipType.DEPENDS_ON;
                                default -> RelationshipType.DEPENDS_ON;
                            };

                            Relationship rel = new Relationship(
                                className,
                                targetModel,
                                relType,
                                fieldType + " relationship",
                                "Django"
                            );

                            relationships.add(rel);
                            log.debug("Found Django relationship: {} --[{}]--> {}", className, fieldType, targetModel);
                            break;
                        }
                    }
                } else {
                    // Regular field
                    String sqlType = mapDjangoFieldToSql(fieldType);
                    boolean nullable = isNullable(fieldArgs);
                    boolean isPrimaryKey = isPrimaryKey(fieldArgs);

                    DataEntity.Field field = new DataEntity.Field(
                        fieldName,
                        sqlType,
                        nullable,
                        null
                    );

                    fields.add(field);

                    if (isPrimaryKey && primaryKey == null) {
                        primaryKey = fieldName;
                    }

                    log.debug("Found Django field: {}.{} ({})", className, fieldName, sqlType);
                }
            }

            // Django models always have an implicit 'id' primary key if not specified
            if (primaryKey == null) {
                primaryKey = "id";
                DataEntity.Field idField = new DataEntity.Field(
                    "id",
                    "AutoField",
                    false,
                    null
                );
                fields.add(0, idField);
            }

            // Create DataEntity
            if (!fields.isEmpty() || !relationships.isEmpty()) {
                DataEntity entity = new DataEntity(
                    className,
                    tableName,
                    "table",
                    fields,
                    primaryKey,
                    "Django Model: " + className
                );

                dataEntities.add(entity);
                log.debug("Found Django model: {} -> table: {}", className, tableName);
            }
        }
    }

    /**
     * Extracts the class body from the source code.
     */
    private String extractClassBody(List<String> lines, int classStartPos, String content) {
        // Find the line number where the class starts
        int lineNumber = content.substring(0, classStartPos).split("\n").length - 1;

        StringBuilder classBody = new StringBuilder();
        int baseIndent = -1;

        for (int i = lineNumber; i < lines.size(); i++) {
            String line = lines.get(i);

            // Skip the class definition line
            if (i == lineNumber) {
                continue;
            }

            // Calculate indentation
            int indent = line.length() - line.trim().length();

            // Initialize base indentation from first non-empty line
            if (baseIndent == -1 && !line.trim().isEmpty()) {
                baseIndent = indent;
            }

            // Stop if we hit another class at the same/lower indentation
            if (baseIndent != -1 && indent < baseIndent && !line.trim().isEmpty()) {
                break;
            }

            classBody.append(line).append("\n");
        }

        return classBody.toString();
    }

    /**
     * Extracts table name from Meta class or generates from class name.
     */
    private String extractTableName(String classBody, String className) {
        Pattern metaTablePattern = Pattern.compile(
            "class\\s+Meta:.*?db_table\\s*=\\s*['\"](.+?)['\"]",
            Pattern.DOTALL
        );

        Matcher matcher = metaTablePattern.matcher(classBody);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Default Django convention: app_modelname
        return toSnakeCase(className);
    }

    /**
     * Maps Django field types to SQL types.
     */
    private String mapDjangoFieldToSql(String djangoField) {
        return switch (djangoField) {
            case "CharField", "TextField", "EmailField", "URLField", "SlugField" -> "VARCHAR";
            case "IntegerField", "SmallIntegerField", "PositiveIntegerField" -> "INTEGER";
            case "BigIntegerField" -> "BIGINT";
            case "BooleanField" -> "BOOLEAN";
            case "DateTimeField" -> "DATETIME";
            case "DateField" -> "DATE";
            case "TimeField" -> "TIME";
            case "DecimalField" -> "DECIMAL";
            case "FloatField" -> "FLOAT";
            case "BinaryField" -> "BLOB";
            case "JSONField" -> "JSON";
            case "UUIDField" -> "UUID";
            case "AutoField", "BigAutoField" -> "SERIAL";
            default -> djangoField;
        };
    }

    /**
     * Checks if the field is nullable.
     */
    private boolean isNullable(String fieldArgs) {
        Matcher nullMatcher = NULL_PATTERN.matcher(fieldArgs);
        if (nullMatcher.find()) {
            return "True".equals(nullMatcher.group(1));
        }

        // Check blank=True as well (implies nullable in practice)
        Matcher blankMatcher = BLANK_PATTERN.matcher(fieldArgs);
        if (blankMatcher.find()) {
            return "True".equals(blankMatcher.group(1));
        }

        return false; // Django fields are NOT NULL by default
    }

    /**
     * Checks if the field is a primary key.
     */
    private boolean isPrimaryKey(String fieldArgs) {
        return PRIMARY_KEY_PATTERN.matcher(fieldArgs).find();
    }

    /**
     * Converts CamelCase to snake_case.
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
