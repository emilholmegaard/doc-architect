package com.docarchitect.core.scanner.impl.php;

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
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Laravel Eloquent ORM model declarations in PHP source files.
 *
 * <p>Parses Eloquent model definitions to extract database entities, fields,
 * and relationships between models.
 *
 * <p><b>Supported Model Detection</b></p>
 * <ul>
 *   <li>{@code class User extends Model} - Basic model</li>
 *   <li>{@code class Post extends Illuminate\Database\Eloquent\Model} - Fully qualified</li>
 * </ul>
 *
 * <p><b>Table/Property Extraction</b></p>
 * <ul>
 *   <li>{@code protected $table = 'users';} - Custom table name</li>
 *   <li>{@code protected $primaryKey = 'user_id';} - Custom primary key</li>
 *   <li>{@code protected $fillable = ['name', 'email'];} - Fillable fields</li>
 *   <li>{@code protected $casts = ['verified' => 'boolean'];} - Type casts</li>
 * </ul>
 *
 * <p><b>Relationship Detection</b></p>
 * <ul>
 *   <li>{@code public function posts(): HasMany} - One-to-many</li>
 *   <li>{@code public function user(): BelongsTo} - Many-to-one (inverse)</li>
 *   <li>{@code public function roles(): BelongsToMany} - Many-to-many</li>
 *   <li>{@code public function profile(): HasOne} - One-to-one</li>
 *   <li>{@code public function parent(): MorphTo} - Polymorphic</li>
 * </ul>
 *
 * @see DataEntity
 * @see Relationship
 * @since 1.0.0
 */
public class EloquentModelScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "eloquent-models";
    private static final String SCANNER_DISPLAY_NAME = "Eloquent Model Scanner";
    private static final String PATTERN_PHP_FILES = "**/*.php";
    private static final String PATTERN_MODELS_ROOT = "app/Models/*.php";
    private static final String PATTERN_MODELS_NESTED = "**/app/Models/*.php";
    private static final String PATTERN_MODEL_SUFFIX = "**/*Model.php";

    private static final int SCANNER_PRIORITY = 60;

    /**
     * Regex to match Eloquent model class definition.
     * Matches: class User extends Model or class User extends Illuminate\Database\Eloquent\Model
     * Captures: (1) class name
     */
    private static final Pattern MODEL_CLASS_PATTERN = Pattern.compile(
        "class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+extends\\s+(?:Illuminate\\\\Database\\\\Eloquent\\\\)?Model\\b"
    );

    /**
     * Regex to extract PHP namespace.
     * Matches: namespace App\Models;
     * Captures: (1) namespace
     */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
        "namespace\\s+([A-Za-z_][A-Za-z0-9_\\\\]*);",
        Pattern.MULTILINE
    );

    /**
     * Regex to extract custom table name.
     * Matches: protected $table = 'users';
     * Captures: (1) table name
     */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "protected\\s+\\$table\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract custom primary key.
     * Matches: protected $primaryKey = 'user_id';
     * Captures: (1) primary key name
     */
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(
        "protected\\s+\\$primaryKey\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract fillable fields.
     * Matches: protected $fillable = ['name', 'email', 'password'];
     * Captures: (1) array content
     */
    private static final Pattern FILLABLE_PATTERN = Pattern.compile(
        "protected\\s+\\$fillable\\s*=\\s*\\[([^\\]]+)\\]"
    );

    /**
     * Regex to extract casts definition.
     * Matches: protected $casts = ['email_verified_at' => 'datetime'];
     * Captures: (1) array content
     */
    private static final Pattern CASTS_PATTERN = Pattern.compile(
        "protected\\s+\\$casts\\s*=\\s*\\[([^\\]]+)\\]"
    );

    /**
     * Regex to extract guarded fields.
     * Matches: protected $guarded = ['id'];
     * Captures: (1) array content
     */
    private static final Pattern GUARDED_PATTERN = Pattern.compile(
        "protected\\s+\\$guarded\\s*=\\s*\\[([^\\]]+)\\]"
    );

    /**
     * Regex to extract relationships from method definitions.
     * Matches: public function posts(): HasMany or public function user(): BelongsTo
     * Captures: (1) method name, (2) relationship type
     *
     * Note: Longer alternatives must come first (e.g., BelongsToMany before BelongsTo)
     */
    private static final Pattern RELATIONSHIP_METHOD_PATTERN = Pattern.compile(
        "public\\s+function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(\\s*\\)\\s*:\\s*(HasManyThrough|HasOneThrough|BelongsToMany|MorphToMany|HasMany|HasOne|BelongsTo|MorphMany|MorphOne|MorphTo)"
    );

    /**
     * Regex to extract target model from relationship method body.
     * Matches: return $this->hasMany(Post::class) or return $this->belongsTo(User::class)
     * Captures: (1) target class name
     *
     * Note: Longer alternatives must come first (e.g., belongsToMany before belongsTo)
     */
    private static final Pattern RELATIONSHIP_TARGET_PATTERN = Pattern.compile(
        "->(?:hasManyThrough|hasOneThrough|belongsToMany|morphToMany|hasMany|hasOne|belongsTo|morphMany|morphOne|morphTo)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_\\\\]*)::class"
    );

    /**
     * Regex to extract individual field names from array content.
     * Matches: 'name', "email", etc.
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile(
        "['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract cast definitions (field => type).
     * Matches: 'field' => 'type' or "field" => "type"
     */
    private static final Pattern CAST_ENTRY_PATTERN = Pattern.compile(
        "['\"]([^'\"]+)['\"]\\s*=>\\s*['\"]([^'\"]+)['\"]"
    );

    private static final String DATA_ENTITY_TYPE = "table";
    private static final String DESCRIPTION_PREFIX = "Eloquent Model: ";
    private static final String RELATIONSHIP_TECHNOLOGY = "Eloquent";
    private static final String DEFAULT_PRIMARY_KEY = "id";

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
        return Set.of(Technologies.PHP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_PHP_FILES, PATTERN_MODELS_ROOT, PATTERN_MODELS_NESTED, PATTERN_MODEL_SUFFIX);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasPhpFiles()
            .and(ApplicabilityStrategies.hasLaravel()
                .or(ApplicabilityStrategies.hasFileContaining(
                    "extends Model",
                    "Illuminate\\Database\\Eloquent\\Model",
                    "use HasFactory"
                )));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Eloquent models in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        // Scan model directories (both root and nested patterns), using Set to deduplicate
        java.util.Set<Path> modelFiles = new java.util.LinkedHashSet<>();
        context.findFiles(PATTERN_MODELS_ROOT).forEach(modelFiles::add);
        context.findFiles(PATTERN_MODELS_NESTED).forEach(modelFiles::add);
        context.findFiles(PATTERN_MODEL_SUFFIX).forEach(modelFiles::add);

        // Also scan all PHP files for models outside standard locations
        List<Path> otherPhpFiles = context.findFiles(PATTERN_PHP_FILES)
            .filter(f -> !isModelFile(f))
            .toList();

        for (Path modelFile : modelFiles) {
            try {
                parseModelFile(modelFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse Eloquent model: {} - {}", modelFile, e.getMessage());
            }
        }

        for (Path phpFile : otherPhpFiles) {
            try {
                if (shouldScanFile(phpFile)) {
                    parseModelFile(phpFile, dataEntities, relationships);
                }
            } catch (Exception e) {
                log.debug("Failed to parse PHP file for Eloquent models: {} - {}", phpFile, e.getMessage());
            }
        }

        log.info("Found {} Eloquent models and {} relationships", dataEntities.size(), relationships.size());

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

    /**
     * Checks if a file is in a standard Laravel model directory.
     */
    private boolean isModelFile(Path file) {
        String pathStr = file.toString().replace('\\', '/');
        return pathStr.contains("/app/Models/") || pathStr.endsWith("Model.php");
    }

    /**
     * Pre-filters files to only scan those likely to contain Eloquent models.
     */
    private boolean shouldScanFile(Path file) throws IOException {
        String content = readFileContent(file);
        return content.contains("extends Model") ||
               content.contains("Illuminate\\Database\\Eloquent\\Model");
    }

    /**
     * Parses a PHP file for Eloquent model definitions.
     */
    private void parseModelFile(Path file, List<DataEntity> dataEntities, List<Relationship> relationships)
            throws IOException {
        String content = readFileContent(file);

        // Find model class definition
        Matcher modelMatcher = MODEL_CLASS_PATTERN.matcher(content);
        if (!modelMatcher.find()) {
            return; // Not an Eloquent model
        }

        String className = modelMatcher.group(1);
        String namespace = extractNamespace(content);
        String componentId = namespace.isEmpty() ? className : namespace + "\\" + className;

        // Extract table name (default: snake_case plural of class name)
        String tableName = extractTableName(content, className);

        // Extract primary key
        String primaryKey = extractPrimaryKey(content);

        // Extract fields from fillable, guarded, and casts
        List<DataEntity.Field> fields = extractFields(content);

        // Create data entity
        DataEntity entity = new DataEntity(
            componentId,
            tableName,
            DATA_ENTITY_TYPE,
            fields,
            primaryKey,
            DESCRIPTION_PREFIX + className
        );
        dataEntities.add(entity);

        // Extract relationships
        extractRelationships(content, componentId, namespace, relationships);

        log.debug("Found Eloquent model: {} -> table: {}", componentId, tableName);
    }

    /**
     * Extracts the namespace from PHP file content.
     */
    private String extractNamespace(String content) {
        Matcher matcher = NAMESPACE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Extracts the table name from the model or derives it from class name.
     */
    private String extractTableName(String content, String className) {
        Matcher matcher = TABLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Default: snake_case plural of class name
        return toSnakeCasePlural(className);
    }

    /**
     * Extracts the primary key from the model.
     */
    private String extractPrimaryKey(String content) {
        Matcher matcher = PRIMARY_KEY_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : DEFAULT_PRIMARY_KEY;
    }

    /**
     * Extracts fields from fillable, guarded, and casts properties.
     */
    private List<DataEntity.Field> extractFields(String content) {
        List<DataEntity.Field> fields = new ArrayList<>();
        java.util.Set<String> fieldNames = new java.util.HashSet<>();

        // Extract from fillable
        Matcher fillableMatcher = FILLABLE_PATTERN.matcher(content);
        if (fillableMatcher.find()) {
            extractFieldNames(fillableMatcher.group(1), fieldNames);
        }

        // Extract from guarded
        Matcher guardedMatcher = GUARDED_PATTERN.matcher(content);
        if (guardedMatcher.find()) {
            extractFieldNames(guardedMatcher.group(1), fieldNames);
        }

        // Extract casts with types
        java.util.Map<String, String> casts = new java.util.HashMap<>();
        Matcher castsMatcher = CASTS_PATTERN.matcher(content);
        if (castsMatcher.find()) {
            String castsContent = castsMatcher.group(1);
            Matcher castEntryMatcher = CAST_ENTRY_PATTERN.matcher(castsContent);
            while (castEntryMatcher.find()) {
                String fieldName = castEntryMatcher.group(1);
                String castType = castEntryMatcher.group(2);
                casts.put(fieldName, castType);
                fieldNames.add(fieldName);
            }
        }

        // Build field list
        for (String fieldName : fieldNames) {
            String dataType = casts.getOrDefault(fieldName, "mixed");
            fields.add(new DataEntity.Field(fieldName, dataType, true, null));
        }

        return fields;
    }

    /**
     * Extracts field names from array content.
     */
    private void extractFieldNames(String arrayContent, java.util.Set<String> fieldNames) {
        Matcher matcher = FIELD_NAME_PATTERN.matcher(arrayContent);
        while (matcher.find()) {
            fieldNames.add(matcher.group(1));
        }
    }

    /**
     * Extracts relationships from the model.
     */
    private void extractRelationships(String content, String sourceEntity, String namespace,
                                      List<Relationship> relationships) {
        Matcher methodMatcher = RELATIONSHIP_METHOD_PATTERN.matcher(content);

        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String relationshipType = methodMatcher.group(2);

            // Find the relationship target in the method body
            int methodStart = methodMatcher.end();
            int methodEnd = findMethodEnd(content, methodStart);
            String methodBody = content.substring(methodStart, methodEnd);

            String targetEntity = extractRelationshipTarget(methodBody, namespace, methodName);
            if (targetEntity == null) {
                continue;
            }

            String description = relationshipType + " to " + targetEntity;

            Relationship relationship = new Relationship(
                sourceEntity,
                targetEntity,
                RelationshipType.DEPENDS_ON,
                description,
                RELATIONSHIP_TECHNOLOGY
            );
            relationships.add(relationship);

            log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, relationshipType, targetEntity);
        }
    }

    /**
     * Extracts the target entity from a relationship method body.
     */
    private String extractRelationshipTarget(String methodBody, String namespace, String methodName) {
        Matcher matcher = RELATIONSHIP_TARGET_PATTERN.matcher(methodBody);
        if (matcher.find()) {
            String target = matcher.group(1);
            // If it's not fully qualified, assume same namespace
            if (!target.contains("\\")) {
                return namespace.isEmpty() ? target : namespace + "\\" + target;
            }
            return target;
        }
        // If we can't find the target, try to infer from method name
        String inferredClass = toPascalCase(methodName);
        return namespace.isEmpty() ? inferredClass : namespace + "\\" + inferredClass;
    }

    /**
     * Finds the approximate end of a method body.
     */
    private int findMethodEnd(String content, int start) {
        int braceCount = 0;
        boolean inMethod = false;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                braceCount++;
                inMethod = true;
            } else if (c == '}') {
                braceCount--;
                if (inMethod && braceCount == 0) {
                    return i;
                }
            }
        }
        return Math.min(start + 500, content.length()); // Fallback
    }

    /**
     * Converts CamelCase to snake_case plural form.
     * Example: UserProfile -> user_profiles
     */
    private String toSnakeCasePlural(String camelCase) {
        String snakeCase = camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return pluralize(snakeCase);
    }

    /**
     * Simple pluralization.
     */
    private String pluralize(String word) {
        if (word.endsWith("y") && word.length() > 1 && !isVowel(word.charAt(word.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        } else if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ||
                   word.endsWith("ch") || word.endsWith("sh")) {
            return word + "es";
        }
        return word + "s";
    }

    /**
     * Checks if a character is a vowel.
     */
    private boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }

    /**
     * Converts snake_case or lowercase to PascalCase.
     * Example: user_profile -> UserProfile, post -> Post
     */
    private String toPascalCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
