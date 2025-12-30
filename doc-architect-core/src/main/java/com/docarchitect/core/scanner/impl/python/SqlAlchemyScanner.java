package com.docarchitect.core.scanner.impl.python;

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
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.PythonAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for SQLAlchemy ORM model definitions in Python source files.
 *
 * <p>Uses AST parsing via {@link PythonAst} to accurately extract SQLAlchemy model
 * classes, fields, and relationships from Python source code.
 *
 * <p><b>Supported Patterns</b></p>
 *
 * <p><b>Legacy Column() Style (SQLAlchemy 1.x):</b></p>
 * <pre>{@code
 * class User(Base):
 *     __tablename__ = 'users'
 *     id = Column(Integer, primary_key=True)
 *     name = Column(String(100), nullable=False)
 *     email = Column(String(255), unique=True)
 * }</pre>
 *
 * <p><b>Modern mapped_column() Style (SQLAlchemy 2.0+):</b></p>
 * <pre>{@code
 * class User(Base):
 *     __tablename__ = 'users'
 *     id: Mapped[int] = mapped_column(primary_key=True)
 *     name: Mapped[str] = mapped_column(String(100))
 *     email: Mapped[Optional[str]] = mapped_column(String(255))
 * }</pre>
 *
 * <p><b>Relationship Patterns</b></p>
 * <ul>
 *   <li>{@code relationship("User", back_populates="posts")}</li>
 *   <li>{@code relationship("Post", backref="author")}</li>
 * </ul>
 *
 * @see PythonAst
 * @see DataEntity
 * @since 1.0.0
 */
public class SqlAlchemyScanner extends AbstractAstScanner<PythonAst.PythonClass> {

    // --- Regex Patterns ---
    private static final String REGEX_TABLENAME = "__tablename__\\s*=\\s*['\"](.+?)['\"]";
    private static final String REGEX_PRIMARY_KEY = "primary_key\\s*=\\s*True";
    private static final String REGEX_NULLABLE = "nullable\\s*=\\s*(True|False)";
    private static final String REGEX_RELATIONSHIP_TARGET = "relationship\\s*\\(\\s*['\"](.+?)['\"]";
    private static final String REGEX_TABLE_TRUE = "table\\s*=\\s*True";
    private static final String REGEX_FOREIGN_KEY = "foreign_key\\s*=\\s*['\"](.+?)['\"]";

    private static final Pattern TABLENAME_PATTERN = Pattern.compile(REGEX_TABLENAME);
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(REGEX_PRIMARY_KEY);
    private static final Pattern NULLABLE_PATTERN = Pattern.compile(REGEX_NULLABLE);
    private static final Pattern RELATIONSHIP_TARGET_PATTERN = Pattern.compile(REGEX_RELATIONSHIP_TARGET);
    private static final Pattern TABLE_TRUE_PATTERN = Pattern.compile(REGEX_TABLE_TRUE);
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(REGEX_FOREIGN_KEY);

    // --- Magic Numbers ---
    private static final int CLASS_BODY_SEARCH_LIMIT = 500;

    // --- Magic Strings ---
    private static final String SQLALCHEMY_ENTITY_SCANNER_ID = "sqlalchemy-entities";
    private static final String SQLALCHEMY_ENTITY_SCANNER_DISPLAY_NAME = "SQLAlchemy Entity Scanner";
    private static final int SCANNER_PRIORITY = 60;
    private static final String BASE_CLASS_NAME = "Base";
    private static final String SQLALCHEMY_RELATIONSHIP_DESCRIPTION = "SQLAlchemy relationship";
    private static final String SQLALCHEMY_MODEL_PREFIX = "SQLAlchemy Model: ";
    private static final String COLUMN_FUNCTION_NAME = "Column";
    private static final String MAPPED_COLUMN_FUNCTION_NAME = "mapped_column";
    private static final String RELATIONSHIP_FUNCTION_NAME = "relationship";
    private static final String ANY_TYPE_HINT = "Any";
    private static final String TABLENAME_FIELD_NAME = "__tablename__";
    private static final String TABLE_TYPE = "table";
    private static final String SQLALCHEMY_TECHNOLOGY = "SQLAlchemy";
    private static final String PYTHON_FILE_PATTERN = "**/*.py";

    public SqlAlchemyScanner() {
        super(AstParserFactory.getPythonParser());
    }

    @Override
    public String getId() {
        return SQLALCHEMY_ENTITY_SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SQLALCHEMY_ENTITY_SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PYTHON_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PYTHON_FILE_PATTERN);
    }

    /**
     * Pre-filters files to avoid parsing Django or other non-SQLAlchemy Python files.
     *
     * <p>This method checks file content for framework-specific imports before
     * attempting AST parsing, preventing {@link ArrayIndexOutOfBoundsException}
     * and other parser errors when encountering incompatible patterns.
     *
     * <p><b>Detection Logic:</b></p>
     * <ul>
     *   <li><b>Skip:</b> Django files ({@code from django.db import models})</li>
     *   <li><b>Accept:</b> SQLAlchemy imports ({@code from sqlalchemy import})</li>
     *   <li><b>Accept:</b> SQLModel imports ({@code from sqlmodel import})</li>
     *   <li><b>Accept:</b> Declarative base ({@code declarative_base()})</li>
     *   <li><b>Skip:</b> Everything else (no matching imports)</li>
     * </ul>
     *
     * @param file Python file to check
     * @return true if file likely contains SQLAlchemy models, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);

            // Skip Django ORM files
            if (content.contains("from django.db import models") ||
                content.contains("from django.db import") ||
                content.contains("django.db.models")) {
                log.debug("Skipping Django file: {}", file.getFileName());
                return false;
            }

            // Accept SQLAlchemy files
            if (content.contains("from sqlalchemy import") ||
                content.contains("from sqlalchemy.") ||
                content.contains("declarative_base()") ||
                content.contains("from sqlmodel import") ||
                content.contains("SQLModel")) {
                return true;
            }

            // Skip files without SQLAlchemy imports
            log.debug("Skipping non-SQLAlchemy file: {}", file.getFileName());
            return false;

        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {} - {}", file, e.getMessage());
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning SQLAlchemy entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> pythonFiles = context.findFiles(PYTHON_FILE_PATTERN).toList();
        statsBuilder.filesDiscovered(pythonFiles.size());

        if (pythonFiles.isEmpty()) {
            return emptyResult();
        }

        int skippedFiles = 0;

        for (Path pythonFile : pythonFiles) {
            if (!shouldScanFile(pythonFile)) {
                skippedFiles++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<EntityResult> result = parseWithFallback(
                pythonFile,
                classes -> extractEntitiesFromAST(pythonFile, classes),
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
        log.info("Found {} SQLAlchemy entities and {} relationships (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
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
     * @param file the Python file being parsed
     * @param classes the parsed Python classes
     * @return list of entity results (entity + relationships)
     */
    private List<EntityResult> extractEntitiesFromAST(Path file, List<PythonAst.PythonClass> classes) {
        List<EntityResult> results = new ArrayList<>();

        // Read file content for __tablename__ extraction (AST parser skips dunder fields)
        String fileContent = null;
        try {
            fileContent = readFileContent(file);
        } catch (IOException e) {
            log.warn("Failed to read file content for tablename extraction: {} - {}", file, e.getMessage());
        }

        for (PythonAst.PythonClass pythonClass : classes) {
            // Determine if this class represents a database table
            if (!isDatabaseTable(pythonClass, fileContent)) {
                continue;
            }

            // Extract table name from __tablename__ field in file content
            String tableName = extractTableName(pythonClass, fileContent);

            // Extract fields and relationships
            List<DataEntity.Field> fields = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            String primaryKey = null;

            // Only process fields that are actually defined in THIS class, not inherited
            List<PythonAst.Field> classFields = getFieldsDefinedInClass(pythonClass, fileContent);
            log.debug("Class {} has {} fields defined", pythonClass.name(), classFields.size());

            for (PythonAst.Field field : classFields) {
                try {
                    log.debug("Processing field: {}.{}", pythonClass.name(), field.name());
                    // Skip dunder fields and private fields
                    if (field.name().equals(TABLENAME_FIELD_NAME) || field.name().startsWith("_")) {
                        continue;
                    }

                    // Check if this is a relationship field (Relationship() or relationship())
                    boolean isRelationship = field.value() != null &&
                        (field.value().contains(RELATIONSHIP_FUNCTION_NAME + "(") ||
                         field.value().contains("Relationship("));

                    if (isRelationship) {
                        // Extract relationship
                        Relationship rel = extractRelationship(pythonClass.name(), field);
                        if (rel != null) {
                            relationships.add(rel);
                            log.debug("Found SQLAlchemy relationship: {} -> {}", pythonClass.name(), rel.targetId());
                        }
                    } else {
                        // Regular column field
                        String sqlType = extractColumnType(field);
                        boolean nullable = isNullable(field.value());
                        boolean isPrimaryKey = isPrimaryKey(field.value());

                        // Check for foreign key in Field(foreign_key="...")
                        String foreignKeyRef = extractForeignKey(field.value());
                        log.debug("Field {}.{}: value='{}', foreignKeyRef='{}'",
                            pythonClass.name(), field.name(), field.value(), foreignKeyRef);
                        if (foreignKeyRef != null) {
                            // Create relationship for foreign key
                            String targetEntity = extractTargetEntityFromForeignKey(foreignKeyRef);
                            log.debug("Extracted target entity from FK '{}': '{}'", foreignKeyRef, targetEntity);
                            if (targetEntity != null) {
                                Relationship fkRel = new Relationship(
                                    pythonClass.name(),
                                    targetEntity,
                                    RelationshipType.DEPENDS_ON,
                                    "Foreign key reference",
                                    SQLALCHEMY_TECHNOLOGY
                                );
                                relationships.add(fkRel);
                                log.debug("Found foreign key: {} -> {}", pythonClass.name(), targetEntity);
                            }
                        }

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

                        log.debug("Found SQLAlchemy field: {}.{} ({})", pythonClass.name(), field.name(), sqlType);
                    }
                } catch (Exception e) {
                    log.warn("Error processing field {}.{}: {}", pythonClass.name(), field.name(), e.getMessage());
                }
            }

            // Create DataEntity
            if (!fields.isEmpty()) {
                DataEntity entity = new DataEntity(
                    pythonClass.name(),
                    tableName,
                    TABLE_TYPE,
                    fields,
                    primaryKey,
                    SQLALCHEMY_MODEL_PREFIX + pythonClass.name()
                );

                results.add(new EntityResult(entity, relationships));
                log.debug("Found SQLAlchemy entity: {} -> table: {}", pythonClass.name(), tableName);
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

            // Check if file contains SQLAlchemy patterns
            if (!content.contains("class ") ||
                (!content.contains("Column(") && !content.contains("mapped_column("))) {
                return results;
            }

            // Simple regex-based extraction for basic SQLAlchemy models
            Pattern classPattern = Pattern.compile("class\\s+(\\w+)\\s*\\([^)]*Base[^)]*\\):");
            Matcher classMatcher = classPattern.matcher(content);

            while (classMatcher.find()) {
                String className = classMatcher.group(1);

                // Extract table name
                String tableName = className;
                Matcher tablenameMatcher = TABLENAME_PATTERN.matcher(content);
                if (tablenameMatcher.find()) {
                    tableName = tablenameMatcher.group(1);
                } else {
                    tableName = toSnakeCase(className);
                }

                // Extract simple fields using regex
                List<DataEntity.Field> fields = new ArrayList<>();
                Pattern fieldPattern = Pattern.compile("(\\w+)\\s*=\\s*Column\\(");
                Matcher fieldMatcher = fieldPattern.matcher(content);
                while (fieldMatcher.find()) {
                    String fieldName = fieldMatcher.group(1);
                    if (!fieldName.startsWith("_")) {
                        fields.add(new DataEntity.Field(fieldName, "Unknown", true, null));
                    }
                }

                if (!fields.isEmpty()) {
                    DataEntity entity = new DataEntity(
                        className,
                        tableName,
                        TABLE_TYPE,
                        fields,
                        null,
                        SQLALCHEMY_MODEL_PREFIX + className
                    );
                    results.add(new EntityResult(entity, List.of()));
                    log.debug("Fallback parsing found entity: {} -> {}", className, tableName);
                }
            }

            return results;
        };
    }

    /**
     * @deprecated Use {@link #extractEntitiesFromAST(Path, List)} instead
     */
    @Deprecated
    private void parsePythonFile(Path file, List<DataEntity> dataEntities,
                                 List<Relationship> relationships) {
        List<PythonAst.PythonClass> classes = parseAstFile(file);

        // Read file content for __tablename__ extraction (AST parser skips dunder fields)
        String fileContent = null;
        try {
            fileContent = readFileContent(file);
        } catch (IOException e) {
            log.warn("Failed to read file content for tablename extraction: {} - {}", file, e.getMessage());
        }

        for (PythonAst.PythonClass pythonClass : classes) {
            // Determine if this class represents a database table
            if (!isDatabaseTable(pythonClass, fileContent)) {
                continue;
            }

            // Extract table name from __tablename__ field in file content
            String tableName = extractTableName(pythonClass, fileContent);

            // Extract fields and relationships
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            // Only process fields that are actually defined in THIS class, not inherited
            List<PythonAst.Field> classFields = getFieldsDefinedInClass(pythonClass, fileContent);
            log.debug("Class {} has {} fields defined", pythonClass.name(), classFields.size());

            for (PythonAst.Field field : classFields) {
                try {
                    log.debug("Processing field: {}.{}", pythonClass.name(), field.name());
                    // Skip dunder fields and private fields
                    if (field.name().equals(TABLENAME_FIELD_NAME) || field.name().startsWith("_")) {
                        continue;
                    }

                // Check if this is a relationship field (Relationship() or relationship())
                boolean isRelationship = field.value() != null &&
                    (field.value().contains(RELATIONSHIP_FUNCTION_NAME + "(") ||
                     field.value().contains("Relationship("));

                if (isRelationship) {
                    // Extract relationship
                    Relationship rel = extractRelationship(pythonClass.name(), field);
                    if (rel != null) {
                        relationships.add(rel);
                        log.debug("Found SQLAlchemy relationship: {} -> {}", pythonClass.name(), rel.targetId());
                    }
                } else {
                    // Regular column field
                    // Can be: Column(), mapped_column(), Field(), or simple type annotation
                    String sqlType = extractColumnType(field);
                    boolean nullable = isNullable(field.value());
                    boolean isPrimaryKey = isPrimaryKey(field.value());

                    // Check for foreign key in Field(foreign_key="...")
                    String foreignKeyRef = extractForeignKey(field.value());
                    log.debug("Field {}.{}: value='{}', foreignKeyRef='{}'",
                        pythonClass.name(), field.name(), field.value(), foreignKeyRef);
                    if (foreignKeyRef != null) {
                        // Create relationship for foreign key
                        String targetEntity = extractTargetEntityFromForeignKey(foreignKeyRef);
                        log.debug("Extracted target entity from FK '{}': '{}'", foreignKeyRef, targetEntity);
                        if (targetEntity != null) {
                            Relationship fkRel = new Relationship(
                                pythonClass.name(),
                                targetEntity,
                                RelationshipType.DEPENDS_ON,
                                "Foreign key reference",
                                SQLALCHEMY_TECHNOLOGY
                            );
                            relationships.add(fkRel);
                            log.debug("Found foreign key: {} -> {}", pythonClass.name(), targetEntity);
                        }
                    }

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

                    log.debug("Found SQLAlchemy field: {}.{} ({})", pythonClass.name(), field.name(), sqlType);
                }
                } catch (Exception e) {
                    log.warn("Error processing field {}.{}: {}", pythonClass.name(), field.name(), e.getMessage());
                }
            }

            // Create DataEntity
            if (!fields.isEmpty()) {
                DataEntity entity = new DataEntity(
                    pythonClass.name(),
                    tableName,
                    TABLE_TYPE,
                    fields,
                    primaryKey,
                    SQLALCHEMY_MODEL_PREFIX + pythonClass.name()
                );

                dataEntities.add(entity);
                log.debug("Found SQLAlchemy entity: {} -> table: {}", pythonClass.name(), tableName);
            }
        }
    }

    /**
     * Extracts fields directly from the class body source code.
     *
     * <p>NOTE: The Python AST parser has issues with SQLModel - it often returns
     * incomplete field lists, missing simple type annotations and Relationship fields,
     * and sometimes mixes fields from different classes. To work around this, we
     * parse fields directly from the source code using regex.</p>
     *
     * @param pythonClass the class to get fields for
     * @param fileContent the source file content (may be null)
     * @return list of fields defined in this class
     */
    private List<PythonAst.Field> getFieldsDefinedInClass(PythonAst.PythonClass pythonClass, String fileContent) {
        if (fileContent == null) {
            return pythonClass.fields();
        }

        // Find the class definition in the file
        String classPattern = "class\\s+" + Pattern.quote(pythonClass.name()) + "\\s*\\([^)]*\\):";
        Pattern pattern = Pattern.compile(classPattern);
        Matcher classMatcher = pattern.matcher(fileContent);

        if (!classMatcher.find()) {
            // Can't find class definition, use regex parsing as fallback
            log.debug("Could not find class definition for {}", pythonClass.name());
            return List.of();
        }

        int classStart = classMatcher.end();

        // Find the end of the class by looking for the next class definition or end of file
        // or the next line with zero indentation (simple heuristic)
        String remainingContent = fileContent.substring(classStart);
        String[] lines = remainingContent.split("\n");

        // Build the class body
        StringBuilder classBody = new StringBuilder();
        boolean foundFirstIndentedLine = false;

        for (String line : lines) {
            // Skip empty lines and comments at the start
            if (!foundFirstIndentedLine && (line.trim().isEmpty() || line.trim().startsWith("#"))) {
                continue;
            }

            // Check if this is an indented line (part of the class)
            if (line.length() > 0 && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                classBody.append(line).append("\n");
                foundFirstIndentedLine = true;
            } else if (foundFirstIndentedLine && !line.trim().isEmpty()) {
                // Hit a non-indented, non-empty line - end of class
                break;
            }
        }

        String classBodyStr = classBody.toString();

        // Parse fields directly from class body
        // Pattern 1: field_name: type = value  OR  field_name: type  (modern syntax)
        // Pattern 2: field_name = value  (legacy Column() syntax)
        Pattern modernPattern = Pattern.compile("^\\s*([a-z_][a-z0-9_]*)\\s*:\\s*([^=\\n]+?)(?:\\s*=\\s*(.+?))?\\s*$", Pattern.MULTILINE);
        Pattern legacyPattern = Pattern.compile("^\\s*([a-z_][a-z0-9_]*)\\s*=\\s*(.+?)\\s*$", Pattern.MULTILINE);

        List<PythonAst.Field> result = new ArrayList<>();

        // Try modern syntax first (with type annotations)
        Matcher modernMatcher = modernPattern.matcher(classBodyStr);
        while (modernMatcher.find()) {
            String fieldName = modernMatcher.group(1);
            String fieldType = modernMatcher.group(2).trim();
            String fieldValue = modernMatcher.group(3) != null ? modernMatcher.group(3).trim() : null;

            result.add(new PythonAst.Field(fieldName, fieldType, fieldValue, List.of()));
        }

        // If no modern fields found, try legacy syntax (without type annotations)
        if (result.isEmpty()) {
            Matcher legacyMatcher = legacyPattern.matcher(classBodyStr);
            while (legacyMatcher.find()) {
                String fieldName = legacyMatcher.group(1);
                String fieldValue = legacyMatcher.group(2).trim();

                // Legacy syntax doesn't have type annotations, type is in Column() call
                result.add(new PythonAst.Field(fieldName, null, fieldValue, List.of()));
            }
        }

        log.debug("Extracted {} fields from class body for {}", result.size(), pythonClass.name());
        return result;
    }

    /**
     * Determines if a Python class represents a database table.
     *
     * <p>A class is considered a database table if it meets one of these criteria:</p>
     * <ul>
     *   <li><b>Traditional SQLAlchemy:</b> Inherits from {@code Base} and is not the Base class itself</li>
     *   <li><b>SQLModel:</b> Has {@code table=True} parameter in class definition</li>
     * </ul>
     *
     * <p>This filtering is critical to distinguish actual database tables from Pydantic schemas
     * in SQLModel projects, where both inherit from SQLModel but only tables have {@code table=True}.</p>
     *
     * @param pythonClass the class to check
     * @param fileContent the file content for regex matching (may be null)
     * @return true if this class represents a database table
     */
    private boolean isDatabaseTable(PythonAst.PythonClass pythonClass, String fileContent) {
        String className = pythonClass.name();

        // Skip the Base class itself (declarative base)
        if (BASE_CLASS_NAME.equals(className)) {
            log.debug("Skipping Base class: {}", className);
            return false;
        }

        // Check for traditional SQLAlchemy: inherits from Base
        // NOTE: Use exact match to avoid matching "UserBase" etc.
        boolean inheritsFromBase = pythonClass.baseClasses().stream()
            .anyMatch(base -> base.equals(BASE_CLASS_NAME));
        if (inheritsFromBase) {
            log.debug("Found traditional SQLAlchemy model: {}", className);
            return true;
        }

        // Check for SQLModel: has table=True parameter
        boolean hasSqlModelTable = isSqlModelTable(pythonClass, fileContent);
        if (hasSqlModelTable) {
            log.debug("Found SQLModel table: {}", className);
            return true;
        }

        // Not a database table - likely a Pydantic schema or unrelated class
        log.debug("Skipping non-table class: {} (no Base inheritance, no table=True)", className);
        return false;
    }

    /**
     * Checks if a class is a SQLModel table (has table=True parameter).
     * SQLModel uses table=True in the class definition to mark database tables,
     * distinguishing them from Pydantic schemas.
     */
    private boolean isSqlModelTable(PythonAst.PythonClass pythonClass, String fileContent) {
        if (fileContent == null) {
            return false;
        }

        // Find class definition: class ClassName(..., table=True)
        String classPattern = "class\\s+" + Pattern.quote(pythonClass.name()) + "\\s*\\([^)]*\\btable\\s*=\\s*True\\b[^)]*\\)";
        Pattern pattern = Pattern.compile(classPattern);
        return pattern.matcher(fileContent).find();
    }

    /**
     * Extracts table name from __tablename__ field or generates from class name.
     * Uses regex to find __tablename__ in file content since AST parser excludes dunder fields.
     */
    private String extractTableName(PythonAst.PythonClass pythonClass, String fileContent) {
        if (fileContent != null) {
            // Find class definition in file
            String classPattern = "class\\s+" + pythonClass.name() + "\\s*\\(";
            Pattern pattern = Pattern.compile(classPattern);
            Matcher classMatcher = pattern.matcher(fileContent);

            if (classMatcher.find()) {
                int classStart = classMatcher.end();
                // Extract class body (simplified - search next 500 chars for __tablename__)
                int searchEnd = Math.min(classStart + CLASS_BODY_SEARCH_LIMIT, fileContent.length());
                String classBody = fileContent.substring(classStart, searchEnd);

                Matcher tablenameMatcher = TABLENAME_PATTERN.matcher(classBody);
                if (tablenameMatcher.find()) {
                    return tablenameMatcher.group(1);
                }
            }
        }

        return toSnakeCase(pythonClass.name());
    }

    /**
     * Extracts relationship information from a field.
     * Handles both relationship() and Relationship() syntax.
     */
    private Relationship extractRelationship(String sourceModel, PythonAst.Field field) {
        if (field.value() == null) {
            return null;
        }

        // Try to extract target from relationship("Target", ...) or Relationship(...)
        // For SQLModel Relationship, the target is in the type hint, not the function call
        String targetModel = null;

        // First, check for relationship("Target", ...) pattern
        Matcher matcher = RELATIONSHIP_TARGET_PATTERN.matcher(field.value());
        if (matcher.find()) {
            targetModel = matcher.group(1);
        }

        // For SQLModel: field_name: list["Target"] = Relationship(...)
        // or: field_name: "Target" | None = Relationship(...)
        // Extract from type hint if present
        if (targetModel == null && field.type() != null) {
            targetModel = extractTargetFromTypeHint(field.type());
        }

        if (targetModel == null) {
            return null;
        }

        return new Relationship(
            sourceModel,
            targetModel,
            RelationshipType.DEPENDS_ON,
            SQLALCHEMY_RELATIONSHIP_DESCRIPTION,
            SQLALCHEMY_TECHNOLOGY
        );
    }

    /**
     * Extracts target entity name from type hint like list["Item"] or "User" | None.
     */
    private String extractTargetFromTypeHint(String typeHint) {
        // Pattern for list["Target"] or List["Target"]
        Pattern listPattern = Pattern.compile("[Ll]ist\\s*\\[\\s*['\"](.+?)['\"]\\s*\\]");
        Matcher listMatcher = listPattern.matcher(typeHint);
        if (listMatcher.find()) {
            return listMatcher.group(1);
        }

        // Pattern for "Target" | None or Optional["Target"]
        Pattern quotedPattern = Pattern.compile("['\"]([A-Z][A-Za-z0-9_]*)['\"]");
        Matcher quotedMatcher = quotedPattern.matcher(typeHint);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }

        return null;
    }

    /**
     * Extracts foreign key reference from Field(foreign_key="table.column").
     */
    private String extractForeignKey(String fieldDef) {
        if (fieldDef == null) {
            return null;
        }
        Matcher matcher = FOREIGN_KEY_PATTERN.matcher(fieldDef);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts target entity name from foreign key reference like "user.id".
     * Converts table name to entity name (e.g., "user" -> "User").
     */
    private String extractTargetEntityFromForeignKey(String foreignKeyRef) {
        if (foreignKeyRef == null || !foreignKeyRef.contains(".")) {
            return null;
        }

        String tableName = foreignKeyRef.substring(0, foreignKeyRef.indexOf("."));

        // Convert snake_case to PascalCase
        String[] parts = tableName.split("_");
        StringBuilder entityName = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                entityName.append(Character.toUpperCase(part.charAt(0)))
                          .append(part.substring(1));
            }
        }

        return entityName.toString();
    }

    /**
     * Extracts column type from field definition.
     * Handles both Column() style and mapped_column() style.
     */
    private String extractColumnType(PythonAst.Field field) {
        String value = field.value();
        if (value == null) {
            return "Unknown";
        }

        // For mapped_column() style: id: Mapped[int] = mapped_column(...)
        // Use the type hint
        if (field.type() != null && !field.type().equals(ANY_TYPE_HINT)) {
            return mapPythonTypeToSql(field.type());
        }

        // For Column() style: id = Column(Integer, ...)
        // Extract the first argument from Column(...)
        if (value.contains(COLUMN_FUNCTION_NAME + "(")) {
            int startIdx = value.indexOf(COLUMN_FUNCTION_NAME + "(") + COLUMN_FUNCTION_NAME.length() + 1;
            int endIdx = value.indexOf(",", startIdx);
            if (endIdx == -1) {
                endIdx = value.indexOf(")", startIdx);
            }
            if (endIdx > startIdx) {
                String type = value.substring(startIdx, endIdx).trim();
                // Remove function calls like String(100)
                if (type.contains("(")) {
                    type = type.substring(0, type.indexOf("("));
                }
                return type;
            }
        }

        return "Unknown";
    }

    /**
     * Maps Python type hints to SQL types.
     * Handles Mapped[T] wrapper and Optional[T] wrapper.
     */
    private String mapPythonTypeToSql(String pythonType) {
        // Remove Mapped wrapper: Mapped[int] -> int
        pythonType = pythonType.replaceAll("Mapped\\[(.+?)\\]", "$1");

        // Remove Optional wrapper: Optional[str] -> str
        pythonType = pythonType.replaceAll("Optional\\[(.+?)\\]", "$1");

        return switch (pythonType.toLowerCase()) {
            case "int" -> "Integer";
            case "str" -> "String";
            case "float" -> "Float";
            case "bool" -> "Boolean";
            case "datetime", "datetime.datetime" -> "DateTime";
            case "date", "datetime.date" -> "Date";
            case "decimal", "decimal.decimal" -> "Numeric";
            default -> pythonType;
        };
    }

    /**
     * Checks if the column is nullable.
     */
    private boolean isNullable(String columnDef) {
        if (columnDef == null) {
            return true; // Default to nullable for simple type annotations
        }
        Matcher matcher = NULLABLE_PATTERN.matcher(columnDef);
        if (matcher.find()) {
            return "True".equals(matcher.group(1));
        }
        return true; // Default to nullable
    }

    /**
     * Checks if the column is a primary key.
     */
    private boolean isPrimaryKey(String columnDef) {
        if (columnDef == null) {
            return false; // Simple type annotations are not primary keys
        }
        return PRIMARY_KEY_PATTERN.matcher(columnDef).find();
    }

    /**
     * Converts CamelCase to snake_case.
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
