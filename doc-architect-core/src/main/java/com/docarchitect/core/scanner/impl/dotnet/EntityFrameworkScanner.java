package com.docarchitect.core.scanner.impl.dotnet;

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
 * Scanner for Entity Framework (EF Core) database entities in C# source files.
 *
 * <p>Uses regex patterns to extract DbContext classes, DbSet properties,
 * entity classes, and navigation properties.
 *
 * <h3>Supported Patterns</h3>
 *
 * <h4>DbContext Declaration</h4>
 * <pre>{@code
 * public class ApplicationDbContext : DbContext
 * {
 *     public DbSet<User> Users { get; set; }
 *     public DbSet<Post> Posts { get; set; }
 *     public DbSet<Comment> Comments { get; set; }
 * }
 * }</pre>
 *
 * <h4>Entity Classes</h4>
 * <pre>{@code
 * public class User
 * {
 *     public int Id { get; set; }
 *     public string Name { get; set; }
 *     public string Email { get; set; }
 *
 *     // Navigation property (one-to-many)
 *     public ICollection<Post> Posts { get; set; }
 * }
 * }</pre>
 *
 * <h4>Navigation Properties</h4>
 * <ul>
 *   <li>{@code public ICollection<T>} - One-to-many relationship</li>
 *   <li>{@code public List<T>} - One-to-many relationship</li>
 *   <li>{@code public T} - Many-to-one or one-to-one relationship</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code DBCONTEXT_PATTERN}: {@code public\s+class\s+(\w+)\s*:\s*DbContext}</li>
 *   <li>{@code DBSET_PATTERN}: {@code public\s+DbSet<(\w+)>\s+(\w+)}</li>
 *   <li>{@code CLASS_PATTERN}: {@code public\s+class\s+(\w+)}</li>
 *   <li>{@code PROPERTY_PATTERN}: {@code public\s+(\w+(?:<[^>]+>)?)\s+(\w+)\s*\{\s*get;\s*set;}</li>
 *   <li>{@code COLLECTION_NAV_PATTERN}: {@code public\s+(?:ICollection|List)<(\w+)>\s+(\w+)}</li>
 * </ul>
 *
 * @see DataEntity
 * @since 1.0.0
 */
public class EntityFrameworkScanner extends AbstractRegexScanner {

    /**
     * Regex to match DbContext class: public class AppDbContext : DbContext.
     * Captures: (1) class name.
     */
    private static final Pattern DBCONTEXT_PATTERN = Pattern.compile(
        "public\\s+class\\s+(\\w+)\\s*:\\s*DbContext"
    );

    /**
     * Regex to match DbSet property: public DbSet<User> Users { get; set; }.
     * Captures: (1) entity type, (2) property name.
     */
    private static final Pattern DBSET_PATTERN = Pattern.compile(
        "public\\s+DbSet<(\\w+)>\\s+(\\w+)\\s*\\{\\s*get;\\s*set;"
    );

    /**
     * Regex to match class declaration: public class User.
     * Captures: (1) class name.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "public\\s+class\\s+(\\w+)\\s*(?::\\s*\\w+)?\\s*(?:\\{|$)"
    );

    /**
     * Regex to match property: public string Name { get; set; }.
     * Captures: (1) type, (2) name.
     */
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "public\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\{\\s*get;\\s*set;"
    );

    /**
     * Regex to match collection navigation property: public ICollection<Post> Posts { get; set; }.
     * Captures: (1) entity type, (2) property name.
     */
    private static final Pattern COLLECTION_NAV_PATTERN = Pattern.compile(
        "public\\s+(?:ICollection|List)<(\\w+)>\\s+(\\w+)\\s*\\{\\s*get;\\s*set;"
    );

    /**
     * Regex to match single navigation property: public User Author { get; set; }.
     * We'll detect these by checking if the type is a known entity.
     */
    private static final Pattern SINGLE_NAV_PATTERN = Pattern.compile(
        "public\\s+(\\w+)\\s+(\\w+)\\s*\\{\\s*get;\\s*set;"
    );

    @Override
    public String getId() {
        return "entity-framework";
    }

    @Override
    public String getDisplayName() {
        return "Entity Framework Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("csharp", "dotnet");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*DbContext.cs", "**/*.cs");
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*DbContext.cs", "**/*.cs");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Entity Framework entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Set<String> entityNames = new HashSet<>();

        List<Path> csFiles = context.findFiles("**/*.cs").toList();

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        // First pass: Find DbContext and collect entity names
        for (Path csFile : csFiles) {
            try {
                collectEntityNamesFromDbContext(csFile, entityNames);
            } catch (Exception e) {
                log.warn("Failed to parse DbContext file: {} - {}", csFile, e.getMessage());
            }
        }

        // Second pass: Parse entity classes
        for (Path csFile : csFiles) {
            try {
                parseEntityClasses(csFile, entityNames, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse entity file: {} - {}", csFile, e.getMessage());
            }
        }

        log.info("Found {} Entity Framework entities and {} relationships",
                dataEntities.size(), relationships.size());

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            dataEntities,
            relationships,
            List.of()
        );
    }

    /**
     * Collects entity names from DbContext's DbSet properties.
     */
    private void collectEntityNamesFromDbContext(Path file, Set<String> entityNames) throws IOException {
        String content = readFileContent(file);

        Matcher dbContextMatcher = DBCONTEXT_PATTERN.matcher(content);
        if (dbContextMatcher.find()) {
            Matcher dbSetMatcher = DBSET_PATTERN.matcher(content);
            while (dbSetMatcher.find()) {
                String entityType = dbSetMatcher.group(1);
                entityNames.add(entityType);
                log.debug("Found entity from DbContext: {}", entityType);
            }
        }
    }

    /**
     * Parses entity classes and extracts properties and relationships.
     */
    private void parseEntityClasses(Path file, Set<String> entityNames,
                                    List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        List<String> lines = readFileLines(file);
        String content = String.join("\n", lines);

        // Find all classes
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);

            // Only process if this is a known entity
            if (!entityNames.contains(className)) {
                continue;
            }

            int classStartPos = classMatcher.start();

            // Extract class body
            String classBody = extractClassBody(lines, classStartPos, content);

            // Extract properties
            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            Matcher propertyMatcher = PROPERTY_PATTERN.matcher(classBody);
            while (propertyMatcher.find()) {
                String type = propertyMatcher.group(1);
                String name = propertyMatcher.group(2);

                // Skip navigation properties (will be handled separately)
                if (type.startsWith("ICollection<") || type.startsWith("List<")) {
                    continue;
                }

                // Skip single navigation properties (references to other entities)
                if (entityNames.contains(type)) {
                    continue;
                }

                // Map C# type to SQL type
                String sqlType = mapCSharpTypeToSql(type);
                boolean nullable = type.contains("?");

                DataEntity.Field field = new DataEntity.Field(
                    name,
                    sqlType,
                    nullable,
                    null
                );

                fields.add(field);

                // Assume "Id" or "ClassName + Id" is primary key
                if (name.equals("Id") || name.equals(className + "Id")) {
                    primaryKey = name;
                }

                log.debug("Found field: {}.{} ({})", className, name, sqlType);
            }

            // Extract collection navigation properties (one-to-many)
            Matcher collectionNavMatcher = COLLECTION_NAV_PATTERN.matcher(classBody);
            while (collectionNavMatcher.find()) {
                String targetEntity = collectionNavMatcher.group(1);
                String propertyName = collectionNavMatcher.group(2);

                Relationship rel = new Relationship(
                    className,
                    targetEntity,
                    RelationshipType.DEPENDS_ON,
                    "One-to-Many relationship",
                    "Entity Framework"
                );

                relationships.add(rel);
                log.debug("Found one-to-many relationship: {} -> {}", className, targetEntity);
            }

            // Extract single navigation properties (many-to-one)
            Matcher singleNavMatcher = SINGLE_NAV_PATTERN.matcher(classBody);
            while (singleNavMatcher.find()) {
                String type = singleNavMatcher.group(1);
                String name = singleNavMatcher.group(2);

                // Only if type is a known entity
                if (entityNames.contains(type)) {
                    Relationship rel = new Relationship(
                        className,
                        type,
                        RelationshipType.DEPENDS_ON,
                        "Many-to-One relationship",
                        "Entity Framework"
                    );

                    relationships.add(rel);
                    log.debug("Found many-to-one relationship: {} -> {}", className, type);
                }
            }

            // Create DataEntity
            if (!fields.isEmpty() || !relationships.isEmpty()) {
                String tableName = toPlural(className); // EF convention: pluralize table names

                DataEntity entity = new DataEntity(
                    className,
                    tableName,
                    "table",
                    fields,
                    primaryKey != null ? primaryKey : "Id",
                    "Entity Framework Entity: " + className
                );

                dataEntities.add(entity);
                log.debug("Found Entity Framework entity: {} -> table: {}", className, tableName);
            }
        }
    }

    /**
     * Extracts the class body from the source code.
     */
    private String extractClassBody(List<String> lines, int classStartPos, String content) {
        int lineNumber = content.substring(0, classStartPos).split("\n").length - 1;

        StringBuilder classBody = new StringBuilder();
        int braceCount = 0;
        boolean inClass = false;

        for (int i = lineNumber; i < lines.size(); i++) {
            String line = lines.get(i);

            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    inClass = true;
                } else if (c == '}') {
                    braceCount--;
                }
            }

            classBody.append(line).append("\n");

            if (inClass && braceCount == 0) {
                break;
            }
        }

        return classBody.toString();
    }

    /**
     * Maps C# types to SQL types.
     */
    private String mapCSharpTypeToSql(String csharpType) {
        // Remove nullable modifier
        csharpType = csharpType.replace("?", "");

        return switch (csharpType.toLowerCase()) {
            case "int", "int32" -> "INTEGER";
            case "long", "int64" -> "BIGINT";
            case "short", "int16" -> "SMALLINT";
            case "byte" -> "TINYINT";
            case "bool", "boolean" -> "BOOLEAN";
            case "string" -> "NVARCHAR";
            case "datetime" -> "DATETIME";
            case "datetimeoffset" -> "DATETIMEOFFSET";
            case "decimal" -> "DECIMAL";
            case "double" -> "FLOAT";
            case "float", "single" -> "REAL";
            case "guid" -> "UNIQUEIDENTIFIER";
            case "byte[]" -> "VARBINARY";
            case "timespan" -> "TIME";
            default -> csharpType.toUpperCase();
        };
    }

    /**
     * Simple pluralization (EF convention).
     * This is a basic implementation - EF uses more sophisticated rules.
     */
    private String toPlural(String singular) {
        if (singular.endsWith("y")) {
            return singular.substring(0, singular.length() - 1) + "ies";
        } else if (singular.endsWith("s") || singular.endsWith("x") ||
                   singular.endsWith("ch") || singular.endsWith("sh")) {
            return singular + "es";
        } else {
            return singular + "s";
        }
    }
}
