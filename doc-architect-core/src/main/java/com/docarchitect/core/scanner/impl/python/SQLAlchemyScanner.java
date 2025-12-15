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
 * Scanner for SQLAlchemy ORM model definitions in Python source files.
 *
 * <p>Since we're running in Java, we parse Python files as TEXT using regex patterns
 * to extract model classes, fields, and relationships.
 *
 * <h3>Supported Patterns</h3>
 *
 * <h4>Legacy Column() Style (SQLAlchemy 1.x)</h4>
 * <pre>{@code
 * class User(Base):
 *     __tablename__ = 'users'
 *     id = Column(Integer, primary_key=True)
 *     name = Column(String(100), nullable=False)
 *     email = Column(String(255), unique=True)
 * }</pre>
 *
 * <h4>Modern mapped_column() Style (SQLAlchemy 2.0+)</h4>
 * <pre>{@code
 * class User(Base):
 *     __tablename__ = 'users'
 *     id: Mapped[int] = mapped_column(primary_key=True)
 *     name: Mapped[str] = mapped_column(String(100))
 *     email: Mapped[Optional[str]] = mapped_column(String(255))
 * }</pre>
 *
 * <h3>Relationship Patterns</h3>
 * <ul>
 *   <li>{@code relationship("User", back_populates="posts")}</li>
 *   <li>{@code relationship("Post", backref="author")}</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code CLASS_PATTERN}: {@code class\s+(\w+)\s*\(.*Base.*\):}</li>
 *   <li>{@code TABLENAME_PATTERN}: {@code __tablename__\s*=\s*['"](.+?)['"]}</li>
 *   <li>{@code COLUMN_PATTERN}: {@code (\w+)\s*=\s*Column\s*\((.+?)\)}</li>
 *   <li>{@code MAPPED_COLUMN_PATTERN}: {@code (\w+):\s*Mapped\[(.+?)\]\s*=\s*mapped_column\((.+?)\)}</li>
 *   <li>{@code RELATIONSHIP_PATTERN}: {@code (\w+)\s*=\s*relationship\s*\(\s*['"](.+?)['"]}</li>
 * </ul>
 *
 * @see DataEntity
 * @since 1.0.0
 */
public class SQLAlchemyScanner extends AbstractRegexScanner {

    /**
     * Regex to match SQLAlchemy model class: class User(Base):.
     * Captures: (1) class name.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "class\\s+(\\w+)\\s*\\(.*Base.*\\):"
    );

    /**
     * Regex to match __tablename__: __tablename__ = 'users'.
     * Captures: (1) table name.
     */
    private static final Pattern TABLENAME_PATTERN = Pattern.compile(
        "__tablename__\\s*=\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex to match Column() fields: id = Column(Integer, primary_key=True).
     * Captures: (1) field name, (2) column definition.
     */
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
        "(\\w+)\\s*=\\s*Column\\s*\\((.+?)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to match mapped_column() fields: id: Mapped[int] = mapped_column(primary_key=True).
     * Captures: (1) field name, (2) mapped type, (3) column definition.
     */
    private static final Pattern MAPPED_COLUMN_PATTERN = Pattern.compile(
        "(\\w+):\\s*Mapped\\[(.+?)\\]\\s*=\\s*mapped_column\\s*\\((.+?)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to match relationship() fields: posts = relationship("Post", back_populates="user").
     * Captures: (1) field name, (2) target model.
     */
    private static final Pattern RELATIONSHIP_PATTERN = Pattern.compile(
        "(\\w+)\\s*=\\s*relationship\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex to extract primary_key=True from column definition.
     */
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(
        "primary_key\\s*=\\s*True"
    );

    /**
     * Regex to extract nullable=False from column definition.
     */
    private static final Pattern NULLABLE_PATTERN = Pattern.compile(
        "nullable\\s*=\\s*(True|False)"
    );

    @Override
    public String getId() {
        return "sqlalchemy-entities";
    }

    @Override
    public String getDisplayName() {
        return "SQLAlchemy Entity Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.py");
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*.py");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning SQLAlchemy entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> pythonFiles = context.findFiles("**/*.py").toList();

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

    private void parsePythonFile(Path file, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        String content = readFileContent(file);
        List<String> lines = readFileLines(file);

        // Find all SQLAlchemy model classes
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int classStartPos = classMatcher.start();

            // Extract class body
            String classBody = extractClassBody(lines, classStartPos, content);

            // Extract table name
            String tableName = extractTableName(classBody, className);

            // Extract fields
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            // Extract Column() style fields
            Matcher columnMatcher = COLUMN_PATTERN.matcher(classBody);
            while (columnMatcher.find()) {
                String fieldName = columnMatcher.group(1);
                String columnDef = columnMatcher.group(2);

                if (fieldName.equals("__tablename__") || fieldName.startsWith("_")) {
                    continue;
                }

                String fieldType = extractColumnType(columnDef);
                boolean nullable = isNullable(columnDef);
                boolean isPrimaryKey = isPrimaryKey(columnDef);

                DataEntity.Field field = new DataEntity.Field(
                    fieldName,
                    fieldType,
                    nullable,
                    null
                );

                fields.add(field);

                if (isPrimaryKey && primaryKey == null) {
                    primaryKey = fieldName;
                }

                log.debug("Found Column field: {}.{} ({})", className, fieldName, fieldType);
            }

            // Extract mapped_column() style fields
            Matcher mappedColumnMatcher = MAPPED_COLUMN_PATTERN.matcher(classBody);
            while (mappedColumnMatcher.find()) {
                String fieldName = mappedColumnMatcher.group(1);
                String mappedType = mappedColumnMatcher.group(2);
                String columnDef = mappedColumnMatcher.group(3);

                if (fieldName.startsWith("_")) {
                    continue;
                }

                String fieldType = mapPythonTypeToSql(mappedType);
                boolean nullable = isNullable(columnDef);
                boolean isPrimaryKey = isPrimaryKey(columnDef);

                DataEntity.Field field = new DataEntity.Field(
                    fieldName,
                    fieldType,
                    nullable,
                    null
                );

                fields.add(field);

                if (isPrimaryKey && primaryKey == null) {
                    primaryKey = fieldName;
                }

                log.debug("Found mapped_column field: {}.{} ({})", className, fieldName, fieldType);
            }

            // Extract relationships
            Matcher relationshipMatcher = RELATIONSHIP_PATTERN.matcher(classBody);
            while (relationshipMatcher.find()) {
                String fieldName = relationshipMatcher.group(1);
                String targetModel = relationshipMatcher.group(2);

                Relationship rel = new Relationship(
                    className,
                    targetModel,
                    RelationshipType.DEPENDS_ON,
                    "SQLAlchemy relationship",
                    "SQLAlchemy"
                );

                relationships.add(rel);
                log.debug("Found relationship: {} -> {}", className, targetModel);
            }

            // Create DataEntity
            if (!fields.isEmpty()) {
                DataEntity entity = new DataEntity(
                    className,
                    tableName,
                    "table",
                    fields,
                    primaryKey,
                    "SQLAlchemy Model: " + className
                );

                dataEntities.add(entity);
                log.debug("Found SQLAlchemy entity: {} -> table: {}", className, tableName);
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

            // Stop if we hit another class or method at the same/lower indentation
            if (baseIndent != -1 && indent <= baseIndent && !line.trim().isEmpty() &&
                (line.trim().startsWith("class ") || line.trim().startsWith("def "))) {
                break;
            }

            classBody.append(line).append("\n");
        }

        return classBody.toString();
    }

    /**
     * Extracts table name from __tablename__ or generates from class name.
     */
    private String extractTableName(String classBody, String className) {
        Matcher matcher = TABLENAME_PATTERN.matcher(classBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return toSnakeCase(className);
    }

    /**
     * Extracts column type from Column() definition.
     * Example: Column(Integer, ...) → Integer
     */
    private String extractColumnType(String columnDef) {
        String[] parts = columnDef.split(",");
        if (parts.length > 0) {
            String type = parts[0].trim();
            // Remove function calls like String(100)
            if (type.contains("(")) {
                type = type.substring(0, type.indexOf("("));
            }
            return type;
        }
        return "Unknown";
    }

    /**
     * Maps Python type hints to SQL types.
     */
    private String mapPythonTypeToSql(String pythonType) {
        // Remove Optional wrapper
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
