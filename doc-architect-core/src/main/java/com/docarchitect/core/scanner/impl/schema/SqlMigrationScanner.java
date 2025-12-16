package com.docarchitect.core.scanner.impl.schema;

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

import com.docarchitect.core.util.Technologies;

/**
 * Scanner for SQL migration files to extract table definitions and relationships.
 *
 * <p>This scanner parses SQL migration files (Flyway, Liquibase, golang-migrate) using
 * regex patterns to extract CREATE TABLE statements, column definitions, and foreign key
 * relationships.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate SQL migration files using pattern matching</li>
 *   <li>Parse CREATE TABLE statements using regex</li>
 *   <li>Extract column definitions with data types</li>
 *   <li>Detect foreign key constraints → Relationship records</li>
 *   <li>Support multiple SQL dialects (PostgreSQL, MySQL, etc.)</li>
 * </ol>
 *
 * <p><b>Supported Migration Tools:</b>
 * <ul>
 *   <li>Flyway: {@code V1__create_users.sql}, {@code V2__add_orders.sql}</li>
 *   <li>Liquibase: {@code changelog.sql}, {@code db/changelog/*.sql}</li>
 *   <li>golang-migrate: {@code 001_create_users.up.sql}, {@code 002_add_orders.up.sql}</li>
 *   <li>Custom: Any {@code *.sql} files in migration directories</li>
 * </ul>
 *
 * <p><b>Supported SQL Constructs:</b>
 * <ul>
 *   <li>CREATE TABLE statements</li>
 *   <li>Column definitions with types, constraints (NOT NULL, PRIMARY KEY)</li>
 *   <li>FOREIGN KEY constraints (inline and separate)</li>
 *   <li>PRIMARY KEY constraints</li>
 *   <li>Common data types across SQL dialects</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new SqlMigrationScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<DataEntity> tables = result.dataEntities();
 * List<Relationship> foreignKeys = result.relationships();
 * }</pre>
 *
 * <p><b>Example SQL Migration:</b>
 * <pre>{@code
 * CREATE TABLE users (
 *   id BIGINT PRIMARY KEY,
 *   name VARCHAR(255) NOT NULL,
 *   email VARCHAR(255)
 * );
 *
 * CREATE TABLE orders (
 *   id BIGINT PRIMARY KEY,
 *   user_id BIGINT NOT NULL,
 *   total DECIMAL(10,2),
 *   FOREIGN KEY (user_id) REFERENCES users(id)
 * );
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see DataEntity
 * @see Relationship
 * @since 1.0.0
 */
public class SqlMigrationScanner extends AbstractRegexScanner {

    // Regex pattern for CREATE TABLE statements
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
        "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+(?:`?\"?(\\w+)`?\"?)\\s*\\(([^;]+)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Regex pattern for column definitions
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
        "^\\s*(?:`?\"?(\\w+)`?\"?)\\s+([A-Z][A-Z0-9_()\\s,]+?)(?:\\s+(NOT\\s+NULL|PRIMARY\\s+KEY|UNIQUE))*(?:,|$)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Regex pattern for foreign key constraints
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(
        "FOREIGN\\s+KEY\\s*\\((?:`?\"?(\\w+)`?\"?)\\)\\s+REFERENCES\\s+(?:`?\"?(\\w+)`?\"?)\\s*\\((?:`?\"?(\\w+)`?\"?)\\)",
        Pattern.CASE_INSENSITIVE
    );

    // Regex pattern for primary key constraints (separate from column definition)
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(
        "PRIMARY\\s+KEY\\s*\\((?:`?\"?(\\w+)`?\"?)\\)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String getId() {
        return "sql-migration";
    }

    @Override
    public String getDisplayName() {
        return "SQL Migration Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.SQL, Technologies.JAVA, Technologies.GO, Technologies.PYTHON, Technologies.CSHARP, Technologies.RUBY);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(
            "**/V*.sql",           // Flyway versioned migrations
            "**/R__*.sql",         // Flyway repeatable migrations
            "**/*.up.sql",         // golang-migrate up migrations
            "**/changelog*.sql",   // Liquibase
            "**/migrations/*.sql", // Generic migrations directory
            "**/db/*.sql"          // Generic db directory
        );
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*.sql");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning SQL migration files in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        // Find all SQL migration files
        List<Path> sqlFiles = context.findFiles("**/*.sql").toList();

        if (sqlFiles.isEmpty()) {
            log.warn("No SQL migration files found in project");
            return emptyResult();
        }

        for (Path sqlFile : sqlFiles) {
            try {
                parseSqlFile(sqlFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse SQL file: {} - {}", sqlFile, e.getMessage());
                // Continue processing other files
            }
        }

        log.info("Found {} tables and {} foreign key relationships across {} SQL files",
            dataEntities.size(), relationships.size(), sqlFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            List.of(), // No API endpoints
            List.of(), // No message flows
            dataEntities,
            relationships,
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single SQL migration file and extracts table definitions.
     *
     * @param sqlFile path to SQL file
     * @param dataEntities list to add discovered tables
     * @param relationships list to add discovered foreign key relationships
     * @throws IOException if file cannot be read
     */
    private void parseSqlFile(Path sqlFile, List<DataEntity> dataEntities,
                              List<Relationship> relationships) throws IOException {
        String content = readFileContent(sqlFile);
        String componentId = sqlFile.getFileName().toString().replace(".sql", "");

        // Remove SQL comments
        content = removeComments(content);

        // Extract CREATE TABLE statements
        Matcher tableMatcher = CREATE_TABLE_PATTERN.matcher(content);

        while (tableMatcher.find()) {
            String tableName = tableMatcher.group(1);
            String tableBody = tableMatcher.group(2);

            // Extract columns
            List<DataEntity.Field> fields = extractColumns(tableBody);

            // Extract primary key
            String primaryKey = extractPrimaryKey(tableBody, fields);

            // Create data entity for this table
            DataEntity entity = new DataEntity(
                componentId,
                tableName,
                "table",
                fields,
                primaryKey,
                "Database table: " + tableName
            );

            dataEntities.add(entity);
            log.debug("Found table: {} with {} columns", tableName, fields.size());

            // Extract foreign key relationships
            extractForeignKeys(tableBody, tableName, relationships);
        }
    }

    /**
     * Removes SQL comments from content.
     *
     * @param content SQL content
     * @return content without comments
     */
    private String removeComments(String content) {
        // Remove single-line comments (-- comment)
        content = content.replaceAll("--[^\n]*", "");
        // Remove multi-line comments (/* comment */)
        content = content.replaceAll("/\\*.*?\\*/", "");
        return content;
    }

    /**
     * Extracts column definitions from a CREATE TABLE body.
     *
     * @param tableBody table body content
     * @return list of extracted fields
     */
    private List<DataEntity.Field> extractColumns(String tableBody) {
        List<DataEntity.Field> fields = new ArrayList<>();

        // Split by commas (simple approach, may not handle all edge cases)
        String[] lines = tableBody.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Skip constraint definitions
            if (line.toUpperCase().startsWith("PRIMARY KEY") ||
                line.toUpperCase().startsWith("FOREIGN KEY") ||
                line.toUpperCase().startsWith("CONSTRAINT") ||
                line.toUpperCase().startsWith("UNIQUE") ||
                line.toUpperCase().startsWith("CHECK") ||
                line.toUpperCase().startsWith("INDEX") ||
                line.isEmpty()) {
                continue;
            }

            // Extract column name and type
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 2) {
                String columnName = parts[0].replaceAll("[`'\"]", "").replaceAll(",$", "");
                String columnType = parts[1].replaceAll(",$", "");

                // Determine if nullable
                boolean nullable = !line.toUpperCase().contains("NOT NULL");

                DataEntity.Field field = new DataEntity.Field(
                    columnName,
                    columnType,
                    nullable,
                    null
                );
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * Extracts primary key from table definition.
     *
     * @param tableBody table body content
     * @param fields list of fields
     * @return primary key column name or null
     */
    private String extractPrimaryKey(String tableBody, List<DataEntity.Field> fields) {
        // Check for separate PRIMARY KEY constraint
        Matcher pkMatcher = PRIMARY_KEY_PATTERN.matcher(tableBody);
        if (pkMatcher.find()) {
            return pkMatcher.group(1);
        }

        // Check for inline PRIMARY KEY in column definition
        for (String line : tableBody.split("\n")) {
            if (line.toUpperCase().contains("PRIMARY KEY")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0) {
                    return parts[0].replaceAll("[`'\",]", "");
                }
            }
        }

        // Default to 'id' if it exists
        return fields.stream()
            .filter(f -> "id".equalsIgnoreCase(f.name()))
            .findFirst()
            .map(DataEntity.Field::name)
            .orElse(null);
    }

    /**
     * Extracts foreign key relationships from table definition.
     *
     * @param tableBody table body content
     * @param tableName source table name
     * @param relationships list to add discovered relationships
     */
    private void extractForeignKeys(String tableBody, String tableName, List<Relationship> relationships) {
        Matcher fkMatcher = FOREIGN_KEY_PATTERN.matcher(tableBody);

        while (fkMatcher.find()) {
            String columnName = fkMatcher.group(1);
            String referencedTable = fkMatcher.group(2);
            String referencedColumn = fkMatcher.group(3);

            Relationship relationship = new Relationship(
                tableName,
                referencedTable,
                RelationshipType.DEPENDS_ON,
                "Foreign key: " + tableName + "." + columnName + " -> " + referencedTable + "." + referencedColumn,
                "SQL"
            );

            relationships.add(relationship);
            log.debug("Found foreign key: {} -> {}", tableName, referencedTable);
        }
    }
}
