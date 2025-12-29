package com.docarchitect.core.scanner.impl.go;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Go struct definitions with ORM tags (XORM, GORM, sqlx, db).
 *
 * <p>This scanner parses Go source files using regex patterns to extract database
 * model definitions from popular Go ORM frameworks and database libraries.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Go source files (*.go) using pattern matching</li>
 *   <li>Pre-filter files based on struct tag detection (xorm, gorm, db, sql)</li>
 *   <li>Extract struct definitions with database tags</li>
 *   <li>Parse field names, types, and tag attributes</li>
 *   <li>Create DataEntity records for each discovered ORM struct</li>
 * </ol>
 *
 * <p><b>Supported ORM Frameworks:</b>
 * <ul>
 *   <li><b>XORM (Gitea):</b> {@code type User struct { Id int64 `xorm:"pk autoincr"` }}</li>
 *   <li><b>GORM:</b> {@code type Product struct { gorm.Model; Code string `gorm:"uniqueIndex"` }}</li>
 *   <li><b>sqlx:</b> {@code type Person struct { Name string `db:"name"` }}</li>
 *   <li><b>Generic db tags:</b> {@code type Account struct { ID int `db:"id"` }}</li>
 * </ul>
 *
 * <p><b>Tag Pattern Examples:</b>
 * <pre>{@code
 * // XORM (Gitea style)
 * type User struct {
 *     Id       int64  `xorm:"pk autoincr"`
 *     Username string `xorm:"unique not null"`
 *     Email    string `xorm:"varchar(255)"`
 * }
 *
 * // GORM
 * type Product struct {
 *     gorm.Model
 *     Code  string `gorm:"uniqueIndex"`
 *     Price uint   `gorm:"not null"`
 * }
 *
 * // sqlx
 * type Person struct {
 *     FirstName string `db:"first_name"`
 *     LastName  string `db:"last_name"`
 * }
 * }</pre>
 *
 * <p><b>Pre-filtering Strategy:</b>
 * Files are scanned only if they contain struct tags indicating database models:
 * <ul>
 *   <li>`xorm:"..."`</li>
 *   <li>`gorm:"..."`</li>
 *   <li>`db:"..."`</li>
 *   <li>gorm.Model embedded field</li>
 * </ul>
 *
 * <p><b>Regex Patterns:</b>
 * <ul>
 *   <li>{@code STRUCT_DEFINITION}: {@code type\s+(\w+)\s+struct\s*\{([^}]+)\}}</li>
 *   <li>{@code FIELD_WITH_TAG}: {@code (\w+)\s+([^\s`]+)\s+`([^`]+)`}</li>
 *   <li>{@code XORM_TAG}: {@code xorm:"([^"]+)"}</li>
 *   <li>{@code GORM_TAG}: {@code gorm:"([^"]+)"}</li>
 *   <li>{@code DB_TAG}: {@code db:"([^"]+)"}</li>
 *   <li>{@code PRIMARY_KEY}: {@code \bpk\b|\bprimaryKey\b|\bprimary_key\b}</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GoStructScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<DataEntity> entities = result.dataEntities();
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see DataEntity
 * @since 1.0.0
 */
public class GoStructScanner extends AbstractRegexScanner {
    private static final String SCANNER_ID = "go-struct";
    private static final String SCANNER_DISPLAY_NAME = "Go Struct Scanner";
    private static final String GO_FILE_GLOB = "**/*.go";
    private static final Set<String> GO_FILE_PATTERNS = Set.of(GO_FILE_GLOB);
    private static final int PRIORITY = 60;

    // Struct tag patterns to detect ORM models
    private static final String XORM_TAG_MARKER = "`xorm:";
    private static final String GORM_TAG_MARKER = "`gorm:";
    private static final String DB_TAG_MARKER = "`db:";
    private static final String SQL_TAG_MARKER = "`sql:";
    private static final String GORM_MODEL_EMBED = "gorm.Model";

    /**
     * Struct definition pattern (multi-line).
     * Captures: (1) struct name, (2) struct body.
     * Uses DOTALL flag to match across multiple lines.
     */
    private static final Pattern STRUCT_DEFINITION = Pattern.compile(
        "type\\s+(\\w+)\\s+struct\\s*\\{([^}]+)\\}",
        Pattern.DOTALL
    );

    /**
     * Field with tag pattern.
     * Captures: (1) field name, (2) field type, (3) full tag string.
     * Example: Username string `xorm:"unique not null"`
     */
    private static final Pattern FIELD_WITH_TAG = Pattern.compile(
        "(\\w+)\\s+([^\\s`]+)\\s+`([^`]+)`"
    );

    /**
     * Embedded field pattern (no tag).
     * Captures: (1) type name.
     * Example: gorm.Model
     */
    private static final Pattern EMBEDDED_FIELD = Pattern.compile(
        "^\\s*(\\w+\\.\\w+)\\s*$",
        Pattern.MULTILINE
    );

    /**
     * XORM tag pattern.
     * Extracts XORM tag value: xorm:"pk autoincr"
     */
    private static final Pattern XORM_TAG = Pattern.compile(
        "xorm:\"([^\"]+)\""
    );

    /**
     * GORM tag pattern.
     * Extracts GORM tag value: gorm:"uniqueIndex"
     */
    private static final Pattern GORM_TAG = Pattern.compile(
        "gorm:\"([^\"]+)\""
    );

    /**
     * Database tag pattern.
     * Extracts db or sql tag value: db:"user_name"
     */
    private static final Pattern DB_TAG = Pattern.compile(
        "db:\"([^\"]+)\""
    );

    /**
     * SQL tag pattern.
     * Extracts sql tag value: sql:"user_name"
     */
    private static final Pattern SQL_TAG = Pattern.compile(
        "sql:\"([^\"]+)\""
    );

    /**
     * Primary key detection pattern.
     * Matches: pk, primaryKey, primary_key, PRIMARY KEY, etc.
     */
    private static final Pattern PRIMARY_KEY = Pattern.compile(
        "\\bpk\\b|\\bprimaryKey\\b|\\bprimary_key\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * NOT NULL detection pattern.
     */
    private static final Pattern NOT_NULL = Pattern.compile(
        "\\bnot\\s+null\\b|\\bnotnull\\b",
        Pattern.CASE_INSENSITIVE
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
        return Set.of(Technologies.GO, Technologies.GOLANG);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return GO_FILE_PATTERNS;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, GO_FILE_PATTERNS.toArray(new String[0]));
    }

    /**
     * Pre-filter files to only scan those containing ORM struct tags.
     *
     * <p>This avoids scanning Go files that don't contain database models,
     * reducing unnecessary processing and improving performance.
     *
     * @param file path to Go source file
     * @return true if file contains ORM struct tags, false otherwise
     */
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);

            // Check for ORM tags or embedded GORM model
            boolean hasOrmTags =
                content.contains(XORM_TAG_MARKER) ||
                content.contains(GORM_TAG_MARKER) ||
                content.contains(DB_TAG_MARKER) ||
                content.contains(SQL_TAG_MARKER) ||
                content.contains(GORM_MODEL_EMBED);

            if (hasOrmTags) {
                log.debug("Including file with ORM struct tags: {}", file.getFileName());
            }

            return hasOrmTags;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Go struct models in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        Set<Path> goFiles = new LinkedHashSet<>();

        GO_FILE_PATTERNS.forEach(pattern -> context.findFiles(pattern).forEach(goFiles::add));

        if (goFiles.isEmpty()) {
            log.warn("No Go files found in project");
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path goFile : goFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(goFile)) {
                skippedFiles++;
                continue;
            }

            try {
                parseGoStructs(goFile, dataEntities);
                parsedFiles++;
            } catch (Exception e) {
                log.error("Failed to parse Go file: {}", goFile, e);
                // Continue processing other files instead of failing completely
            }
        }

        log.debug("Pre-filtered {} files (no ORM struct tags)", skippedFiles);
        log.info("Found {} data entities across {} Go files (parsed {}/{})",
            dataEntities.size(), goFiles.size(), parsedFiles, goFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            List.of(), // No API endpoints
            List.of(), // No message flows
            dataEntities,
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single Go file and extracts struct definitions with database tags.
     *
     * @param goFile path to Go file
     * @param dataEntities list to add discovered data entities
     * @throws IOException if file cannot be read
     */
    private void parseGoStructs(Path goFile, List<DataEntity> dataEntities) throws IOException {
        String content = readFileContent(goFile);
        String componentId = extractComponentId(goFile, content);

        // Find all struct definitions in the file
        Matcher structMatcher = STRUCT_DEFINITION.matcher(content);

        while (structMatcher.find()) {
            String structName = structMatcher.group(1);
            String structBody = structMatcher.group(2);

            // Check if this struct has database tags
            if (!hasOrmTags(structBody)) {
                log.debug("Skipping struct without ORM tags: {}", structName);
                continue;
            }

            // Parse fields from struct body
            List<DataEntity.Field> fields = parseStructFields(structBody);
            String primaryKey = extractPrimaryKey(structBody);

            if (!fields.isEmpty() || hasGormModel(structBody)) {
                DataEntity entity = new DataEntity(
                    componentId,
                    toSnakeCase(structName), // Table name in snake_case
                    "table",
                    fields,
                    primaryKey,
                    "Go ORM Model: " + structName
                );

                dataEntities.add(entity);
                log.debug("Found Go struct entity: {} -> table: {}", structName, toSnakeCase(structName));
            }
        }
    }

    /**
     * Extracts component ID from package name in Go file.
     *
     * @param goFile path to Go file
     * @param content file content
     * @return component ID (package name)
     */
    private String extractComponentId(Path goFile, String content) {
        // Extract package name from Go file
        Pattern packagePattern = Pattern.compile("^package\\s+(\\w+)", Pattern.MULTILINE);
        Matcher matcher = packagePattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback to parent directory name
        return goFile.getParent().getFileName().toString();
    }

    /**
     * Checks if struct body contains ORM tags.
     *
     * @param structBody struct body content
     * @return true if struct has ORM tags or embedded GORM model
     */
    private boolean hasOrmTags(String structBody) {
        return structBody.contains(XORM_TAG_MARKER) ||
               structBody.contains(GORM_TAG_MARKER) ||
               structBody.contains(DB_TAG_MARKER) ||
               structBody.contains(SQL_TAG_MARKER) ||
               structBody.contains(GORM_MODEL_EMBED);
    }

    /**
     * Checks if tag string contains ORM tag keywords.
     * This method is used on the tag content (without backticks).
     *
     * @param tags tag string content (e.g., 'xorm:"pk"' without backticks)
     * @return true if tags contain ORM keywords
     */
    private boolean hasOrmTagsInString(String tags) {
        return tags.contains("xorm:") ||
               tags.contains("gorm:") ||
               tags.contains("db:") ||
               tags.contains("sql:");
    }

    /**
     * Checks if struct has embedded gorm.Model.
     *
     * @param structBody struct body content
     * @return true if struct embeds gorm.Model
     */
    private boolean hasGormModel(String structBody) {
        return structBody.contains(GORM_MODEL_EMBED);
    }

    /**
     * Parses fields from struct body.
     *
     * @param structBody struct body content
     * @return list of data entity fields
     */
    private List<DataEntity.Field> parseStructFields(String structBody) {
        List<DataEntity.Field> fields = new ArrayList<>();

        // Parse fields with tags
        Matcher fieldMatcher = FIELD_WITH_TAG.matcher(structBody);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String fieldType = fieldMatcher.group(2);
            String tags = fieldMatcher.group(3);

            // Only include fields with database tags
            if (!hasOrmTagsInString(tags)) {
                continue;
            }

            // Extract column name from tag or convert field name
            String columnName = extractColumnName(fieldName, tags);
            String dataType = mapGoTypeToSql(fieldType);
            boolean nullable = !isNotNull(tags);

            DataEntity.Field field = new DataEntity.Field(
                columnName,
                dataType,
                nullable,
                null
            );

            fields.add(field);
            log.debug("Found field: {} ({}) nullable={}", columnName, dataType, nullable);
        }

        // Add standard GORM model fields if gorm.Model is embedded
        if (hasGormModel(structBody)) {
            fields.addAll(getGormModelFields());
        }

        return fields;
    }

    /**
     * Extracts column name from tags or converts field name to snake_case.
     *
     * @param fieldName Go field name
     * @param tags struct tag string
     * @return column name
     */
    private String extractColumnName(String fieldName, String tags) {
        // Try XORM tag: xorm:"column_name ..." or xorm:"'column_name' ..."
        Matcher xormMatcher = XORM_TAG.matcher(tags);
        if (xormMatcher.find()) {
            String tagValue = xormMatcher.group(1);
            // Extract column name (first word before space)
            String[] parts = tagValue.split("\\s+");
            if (parts.length > 0) {
                String firstPart = parts[0];
                // Only treat as column name if it's explicitly quoted or contains underscore
                // This avoids treating SQL types (text, varchar, int) as column names
                if (firstPart.startsWith("'") || firstPart.contains("_")) {
                    return firstPart.replaceAll("['\"]", "");
                }
            }
        }

        // Try GORM tag: gorm:"column:column_name"
        Matcher gormMatcher = GORM_TAG.matcher(tags);
        if (gormMatcher.find()) {
            String tagValue = gormMatcher.group(1);
            Pattern columnPattern = Pattern.compile("column:([^;\\s]+)");
            Matcher columnMatcher = columnPattern.matcher(tagValue);
            if (columnMatcher.find()) {
                return columnMatcher.group(1);
            }
        }

        // Try db/sql tag: db:"column_name"
        Matcher dbMatcher = DB_TAG.matcher(tags);
        if (dbMatcher.find()) {
            return dbMatcher.group(1);
        }

        Matcher sqlMatcher = SQL_TAG.matcher(tags);
        if (sqlMatcher.find()) {
            return sqlMatcher.group(1);
        }

        // Default: convert field name to snake_case
        return toSnakeCase(fieldName);
    }

    /**
     * Extracts primary key field from struct body.
     *
     * @param structBody struct body content
     * @return primary key field name, or null if not found
     */
    private String extractPrimaryKey(String structBody) {
        Matcher fieldMatcher = FIELD_WITH_TAG.matcher(structBody);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String tags = fieldMatcher.group(3);

            if (isPrimaryKey(tags)) {
                return extractColumnName(fieldName, tags);
            }
        }

        // GORM model has ID as primary key by default
        if (hasGormModel(structBody)) {
            return "id";
        }

        return null;
    }

    /**
     * Checks if tag indicates primary key.
     *
     * @param tags struct tag string
     * @return true if field is a primary key
     */
    private boolean isPrimaryKey(String tags) {
        return PRIMARY_KEY.matcher(tags).find();
    }

    /**
     * Checks if tag indicates NOT NULL constraint.
     *
     * @param tags struct tag string
     * @return true if field has NOT NULL constraint
     */
    private boolean isNotNull(String tags) {
        return NOT_NULL.matcher(tags).find();
    }

    /**
     * Maps Go type to SQL type.
     *
     * @param goType Go field type
     * @return SQL type
     */
    private String mapGoTypeToSql(String goType) {
        // Remove pointer prefix
        String cleanType = goType.replace("*", "");

        return switch (cleanType) {
            case "int", "int32", "int64", "uint", "uint32", "uint64" -> "INTEGER";
            case "string" -> "VARCHAR";
            case "bool" -> "BOOLEAN";
            case "float32", "float64" -> "FLOAT";
            case "time.Time" -> "TIMESTAMP";
            case "[]byte" -> "BLOB";
            default -> cleanType;
        };
    }

    /**
     * Returns standard GORM model fields (ID, CreatedAt, UpdatedAt, DeletedAt).
     *
     * @return list of GORM model fields
     */
    private List<DataEntity.Field> getGormModelFields() {
        return List.of(
            new DataEntity.Field("id", "INTEGER", false, "Primary key"),
            new DataEntity.Field("created_at", "TIMESTAMP", true, "Creation timestamp"),
            new DataEntity.Field("updated_at", "TIMESTAMP", true, "Update timestamp"),
            new DataEntity.Field("deleted_at", "TIMESTAMP", true, "Soft delete timestamp")
        );
    }

    /**
     * Converts CamelCase to snake_case.
     *
     * @param camelCase CamelCase string
     * @return snake_case string
     */
    private String toSnakeCase(String camelCase) {
        return camelCase
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
            .toLowerCase();
    }
}
