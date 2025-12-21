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
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.PythonAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
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

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning SQLAlchemy entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> pythonFiles = context.findFiles(PYTHON_FILE_PATTERN).toList();

        if (pythonFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path pythonFile : pythonFiles) {
            try {
                parsePythonFile(pythonFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse Python file: {} - {}", pythonFile, e.getMessage());
            }
        }

        log.info("Found {} SQLAlchemy entities and {} relationships", dataEntities.size(), relationships.size());

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

            for (PythonAst.Field field : classFields) {
                // Skip dunder fields and private fields
                if (field.name().equals(TABLENAME_FIELD_NAME) || field.name().startsWith("_")) {
                    continue;
                }

                // Check if this is a relationship field (Relationship() or relationship())
                if (field.value() != null &&
                    (field.value().contains(RELATIONSHIP_FUNCTION_NAME + "(") ||
                     field.value().contains("Relationship("))) {
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
                    if (foreignKeyRef != null) {
                        // Create relationship for foreign key
                        String targetEntity = extractTargetEntityFromForeignKey(foreignKeyRef);
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
     * Filters fields to only include those actually defined in the class body,
     * excluding inherited fields from parent classes.
     *
     * <p>The AST parser returns all fields including inherited ones. This method
     * uses the source file content to determine which fields are actually defined
     * in this specific class.</p>
     *
     * @param pythonClass the class to get fields for
     * @param fileContent the source file content (may be null)
     * @return list of fields defined in this class (not inherited)
     */
    private List<PythonAst.Field> getFieldsDefinedInClass(PythonAst.PythonClass pythonClass, String fileContent) {
        if (fileContent == null || pythonClass.fields().isEmpty()) {
            return pythonClass.fields();
        }

        // Find the class definition in the file
        String classPattern = "class\\s+" + Pattern.quote(pythonClass.name()) + "\\s*\\([^)]*\\):";
        Pattern pattern = Pattern.compile(classPattern);
        Matcher classMatcher = pattern.matcher(fileContent);

        if (!classMatcher.find()) {
            // Can't find class definition, return all fields (fallback)
            log.debug("Could not find class definition for {}, using all fields", pythonClass.name());
            return pythonClass.fields();
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

        // Filter fields: only keep fields whose names appear in the class body
        List<PythonAst.Field> result = pythonClass.fields().stream()
            .filter(field -> {
                // Check if this field name appears in the class body with an assignment
                String fieldPattern = "^\\s*" + Pattern.quote(field.name()) + "\\s*[:=]";
                Pattern p = Pattern.compile(fieldPattern, Pattern.MULTILINE);
                boolean found = p.matcher(classBodyStr).find();
                if (!found) {
                    log.debug("Excluding inherited field: {}.{}", pythonClass.name(), field.name());
                } else {
                    log.debug("Including field: {}.{} (value={})", pythonClass.name(), field.name(), field.value());
                }
                return found;
            })
            .toList();

        log.debug("Class {} has {} total fields, {} defined in class",
            pythonClass.name(), pythonClass.fields().size(), result.size());
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
        return PRIMARY_KEY_PATTERN.matcher(columnDef).find();
    }

    /**
     * Converts CamelCase to snake_case.
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
