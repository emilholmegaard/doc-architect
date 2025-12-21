package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.DotNetAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.util.Technologies;

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
 * <p><b>Supported Patterns</b></p>
 *
 * <p><b>DbContext Declaration:</b></p>
 * <pre>{@code
 * public class ApplicationDbContext : DbContext
 * {
 *     public DbSet<User> Users { get; set; }
 *     public DbSet<Post> Posts { get; set; }
 *     public DbSet<Comment> Comments { get; set; }
 * }
 * }</pre>
 *
 * <p><b>Entity Classes:</b></p>
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
 * <p><b>Navigation Properties:</b></p>
 * <ul>
 *   <li>{@code public ICollection<T>} - One-to-many relationship</li>
 *   <li>{@code public List<T>} - One-to-many relationship</li>
 *   <li>{@code public T} - Many-to-one or one-to-one relationship</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
 * <ul>
 *   <li>DBCONTEXT_PATTERN: Matches DbContext class declarations</li>
 *   <li>DBSET_PATTERN: Matches DbSet property declarations</li>
 *   <li>CLASS_PATTERN: Matches class declarations</li>
 *   <li>PROPERTY_PATTERN: Matches property declarations with getters/setters</li>
 *   <li>COLLECTION_NAV_PATTERN: Matches navigation property collections</li>
 * </ul>
 *
 * @see DataEntity
 * @since 1.0.0
 */
public class EntityFrameworkScanner extends AbstractAstScanner<DotNetAst.CSharpClass> {

    // Scanner identification constants
    private static final String SCANNER_ID = "entity-framework";
    private static final String SCANNER_NAME = "Entity Framework Scanner";
    private static final int SCANNER_PRIORITY = 60;

    public EntityFrameworkScanner() {
        super(AstParserFactory.getDotNetParser());
    }
    
    // File pattern constants
    private static final String DBCONTEXT_FILE_PATTERN = "**/*DbContext.cs";
    private static final String CS_FILE_PATTERN = "**/*.cs";
    
    // Regex pattern strings
    private static final String DBCONTEXT_REGEX = "public\\s+class\\s+(\\w+)\\s*:\\s*DbContext";
    private static final String DBSET_REGEX = "public\\s+DbSet<(\\w+)>\\s+(\\w+)\\s*\\{[^}]*get;[^}]*set;[^}]*\\}";
    private static final String CLASS_REGEX = "public\\s+class\\s+(\\w+)\\s*(?::\\s*\\w+)?\\s*(?:\\{|$)";
    private static final String PROPERTY_REGEX = "public\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\{[^}]*get;[^}]*set;[^}]*\\}";
    private static final String COLLECTION_NAV_REGEX = "public\\s+(?:ICollection|List)<(\\w+)>\\s+(\\w+)\\s*\\{[^}]*get;[^}]*set;[^}]*\\}";
    private static final String SINGLE_NAV_REGEX = "public\\s+(\\w+)\\s+(\\w+)\\s*\\{[^}]*get;[^}]*set;[^}]*\\}";
    
    // Relationship type constants
    private static final String ONE_TO_MANY_DESCRIPTION = "One-to-Many relationship";
    private static final String MANY_TO_ONE_DESCRIPTION = "Many-to-One relationship";
    private static final String RELATIONSHIP_SOURCE = "Entity Framework";
    private static final String ENTITY_TYPE = "table";
    
    // Type name constants
    private static final String ICOLLECTION_TYPE = "ICollection<";
    private static final String LIST_TYPE = "List<";
    
    // Regex compile flags
    private static final int REGEX_FLAGS = Pattern.DOTALL;

    /**
     * Regex to match DbContext class: public class AppDbContext : DbContext.
     * Captures: (1) class name.
     */
    private static final Pattern DBCONTEXT_PATTERN = Pattern.compile(DBCONTEXT_REGEX);

    /**
     * Regex to match DbSet property: public DbSet<User> Users { get; set; }.
     * Captures: (1) entity type, (2) property name.
     */
    private static final Pattern DBSET_PATTERN = Pattern.compile(DBSET_REGEX, REGEX_FLAGS);

    /**
     * Regex to match class declaration: public class User.
     * Captures: (1) class name.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(CLASS_REGEX);

    /**
     * Regex to match property: public string Name { get; set; }.
     * Captures: (1) type, (2) name.
     */
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(PROPERTY_REGEX, REGEX_FLAGS);

    /**
     * Regex to match collection navigation property: public ICollection<Post> Posts { get; set; }.
     * Captures: (1) entity type, (2) property name.
     */
    private static final Pattern COLLECTION_NAV_PATTERN = Pattern.compile(COLLECTION_NAV_REGEX, REGEX_FLAGS);

    /**
     * Regex to match single navigation property: public User Author { get; set; }.
     * Captures: (1) type, (2) name.
     */
    private static final Pattern SINGLE_NAV_PATTERN = Pattern.compile(SINGLE_NAV_REGEX, REGEX_FLAGS);

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.CSHARP, Technologies.DOTNET);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(DBCONTEXT_FILE_PATTERN, CS_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, DBCONTEXT_FILE_PATTERN, CS_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Entity Framework entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Set<String> entityNames = new HashSet<>();

        List<Path> csFiles = context.findFiles(CS_FILE_PATTERN).toList();

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
        // Use AST parser to parse C# file
        List<DotNetAst.CSharpClass> classes = parseAstFile(file);

        for (DotNetAst.CSharpClass csharpClass : classes) {
            String className = csharpClass.name();

            // Only process if this is a known entity
            if (!entityNames.contains(className)) {
                continue;
            }

            // Extract properties and detect primary key
            List<DataEntity.Field> fields = extractEntityFieldsFromAst(csharpClass, entityNames);
            String primaryKey = findPrimaryKeyFromProperties(csharpClass);

            // Extract relationships
            extractNavigationRelationshipsFromAst(csharpClass, entityNames, relationships);

            // Create DataEntity
            if (!fields.isEmpty()) {
                String tableName = toPlural(className);

                DataEntity entity = new DataEntity(
                    className,
                    tableName,
                    ENTITY_TYPE,
                    fields,
                    primaryKey,
                    "Entity Framework Entity: " + className
                );

                dataEntities.add(entity);
                log.debug("Found Entity Framework entity: {} -> table: {} with PK: {}",
                    className, tableName, primaryKey != null ? primaryKey : "none");
            }
        }
    }

    /**
     * Extracts entity fields from AST, filtering out navigation properties.
     */
    private List<DataEntity.Field> extractEntityFieldsFromAst(DotNetAst.CSharpClass csharpClass, Set<String> entityNames) {
        List<DataEntity.Field> fields = new ArrayList<>();

        for (DotNetAst.Property property : csharpClass.properties()) {
            String type = property.type();
            String name = property.name();

            // Skip navigation properties
            if (isNavigationProperty(type, entityNames)) {
                continue;
            }

            String sqlType = mapCSharpTypeToSql(type);
            boolean nullable = type.contains("?");

            DataEntity.Field field = new DataEntity.Field(name, sqlType, nullable, null);
            fields.add(field);

            log.debug("Found field: {}.{} ({})", csharpClass.name(), name, sqlType);
        }

        return fields;
    }

    /**
     * Determines if a property has the [Key] attribute.
     */
    private boolean hasKeyAttribute(DotNetAst.Property property) {
        return property.attributes().stream()
            .anyMatch(attr -> "Key".equals(attr.name()));
    }

    /**
     * Determines if a field is a primary key by EF convention or [Key] attribute.
     *
     * <p>EF Core conventions:
     * <ul>
     *   <li>Field named "Id" (case-insensitive)</li>
     *   <li>Field named "{ClassName}Id" (case-insensitive)</li>
     *   <li>Field decorated with [Key] attribute</li>
     * </ul>
     *
     * @param property the property to check
     * @param className the entity class name
     * @return true if this property is a primary key
     */
    private boolean isPrimaryKeyByConvention(DotNetAst.Property property, String className) {
        String propertyName = property.name();

        // Check for [Key] attribute
        if (hasKeyAttribute(property)) {
            return true;
        }

        // EF convention: "Id"
        if ("Id".equalsIgnoreCase(propertyName)) {
            return true;
        }

        // EF convention: "{ClassName}Id"
        String conventionName = className + "Id";
        if (conventionName.equalsIgnoreCase(propertyName)) {
            return true;
        }

        return false;
    }

    /**
     * Determines if a type is a navigation property.
     */
    private boolean isNavigationProperty(String type, Set<String> entityNames) {
        return type.startsWith(ICOLLECTION_TYPE) || 
               type.startsWith(LIST_TYPE) || 
               entityNames.contains(type);
    }

    /**
     * Finds the primary key field from the class properties using EF conventions and [Key] attribute.
     *
     * @param csharpClass the entity class
     * @return the primary key field name, or null if not found
     */
    private String findPrimaryKeyFromProperties(DotNetAst.CSharpClass csharpClass) {
        String className = csharpClass.name();

        // Check all properties for primary key
        for (DotNetAst.Property property : csharpClass.properties()) {
            // Skip navigation properties
            String type = property.type();
            if (type.startsWith(ICOLLECTION_TYPE) || type.startsWith(LIST_TYPE)) {
                continue;
            }

            if (isPrimaryKeyByConvention(property, className)) {
                log.debug("Detected primary key: {}.{}", className, property.name());
                return property.name();
            }
        }

        log.debug("No primary key detected for entity: {}", className);
        return null;
    }

    /**
     * Extracts navigation relationships from AST properties.
     */
    private void extractNavigationRelationshipsFromAst(DotNetAst.CSharpClass csharpClass,
                                                       Set<String> entityNames, List<Relationship> relationships) {
        String className = csharpClass.name();

        for (DotNetAst.Property property : csharpClass.properties()) {
            String type = property.type();

            // Check for collection navigation properties (one-to-many)
            if (type.startsWith(ICOLLECTION_TYPE) || type.startsWith(LIST_TYPE)) {
                // Extract generic type parameter
                String targetEntity = extractGenericType(type);
                if (targetEntity != null && entityNames.contains(targetEntity)) {
                    addRelationship(className, targetEntity, ONE_TO_MANY_DESCRIPTION, relationships);
                }
            }
            // Check for single navigation properties (many-to-one)
            else if (entityNames.contains(type)) {
                addRelationship(className, type, MANY_TO_ONE_DESCRIPTION, relationships);
            }
        }
    }

    /**
     * Extracts generic type parameter from a generic type (e.g., "ICollection<Post>" -> "Post").
     */
    private String extractGenericType(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start != -1 && end != -1 && end > start) {
            return type.substring(start + 1, end).trim();
        }
        return null;
    }

    /**
     * Adds a relationship between two entities.
     */
    private void addRelationship(String sourceEntity, String targetEntity, String description, 
                                List<Relationship> relationships) {
        Relationship rel = new Relationship(
            sourceEntity,
            targetEntity,
            RelationshipType.DEPENDS_ON,
            description,
            RELATIONSHIP_SOURCE
        );
        
        relationships.add(rel);
        log.debug("Found relationship: {} -> {} ({})", sourceEntity, targetEntity, description);
    }

    /**
     * Extracts the class body from the source code.
     */
    private String extractClassBody(List<String> lines, int classStartPos, String content) {
        int lineNumber = content.substring(0, classStartPos).split("\n").length - 1;

        StringBuilder classBody = new StringBuilder();
        int openBraces = 0;
        boolean classBodyStarted = false;

        for (int i = lineNumber; i < lines.size(); i++) {
            String line = lines.get(i);

            for (char c : line.toCharArray()) {
                if (c == '{') {
                    openBraces++;
                    classBodyStarted = true;
                } else if (c == '}') {
                    openBraces--;
                }
            }

            classBody.append(line).append("\n");

            if (classBodyStarted && openBraces == 0) {
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
