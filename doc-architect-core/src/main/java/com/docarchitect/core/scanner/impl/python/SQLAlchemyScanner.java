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

    private static final Pattern TABLENAME_PATTERN = Pattern.compile(REGEX_TABLENAME);
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(REGEX_PRIMARY_KEY);
    private static final Pattern NULLABLE_PATTERN = Pattern.compile(REGEX_NULLABLE);
    private static final Pattern RELATIONSHIP_TARGET_PATTERN = Pattern.compile(REGEX_RELATIONSHIP_TARGET);

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
            // Skip non-SQLAlchemy models (must inherit from Base)
            if (!pythonClass.inheritsFrom(BASE_CLASS_NAME)) {
                continue;
            }

            // Skip the Base class itself (declarative base)
            if (pythonClass.name().equals(BASE_CLASS_NAME)) {
                continue;
            }

            // Extract table name from __tablename__ field in file content
            String tableName = extractTableName(pythonClass, fileContent);

            // Extract fields and relationships
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            for (PythonAst.Field field : pythonClass.fields()) {
                if (field.name().equals(TABLENAME_FIELD_NAME) || field.name().startsWith("_")) {
                    continue;
                }

                // Check if this is a relationship field
                if (field.value() != null && field.value().contains(RELATIONSHIP_FUNCTION_NAME + "(")) {
                    // Extract relationship
                    Relationship rel = extractRelationship(pythonClass.name(), field);
                    if (rel != null) {
                        relationships.add(rel);
                        log.debug("Found SQLAlchemy relationship: {} -> {}", pythonClass.name(), rel.targetId());
                    }
                } else if (field.value() != null &&
                          (field.value().contains(COLUMN_FUNCTION_NAME + "(") || field.value().contains(MAPPED_COLUMN_FUNCTION_NAME + "("))) {
                    // Regular column field
                    String sqlType = extractColumnType(field);
                    boolean nullable = isNullable(field.value());
                    boolean isPrimaryKey = isPrimaryKey(field.value());

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
     */
    private Relationship extractRelationship(String sourceModel, PythonAst.Field field) {
        if (field.value() == null) {
            return null;
        }

        Matcher matcher = RELATIONSHIP_TARGET_PATTERN.matcher(field.value());
        if (!matcher.find()) {
            return null;
        }

        String targetModel = matcher.group(1);

        return new Relationship(
            sourceModel,
            targetModel,
            RelationshipType.DEPENDS_ON,
            SQLALCHEMY_RELATIONSHIP_DESCRIPTION,
            SQLALCHEMY_TECHNOLOGY
        );
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
