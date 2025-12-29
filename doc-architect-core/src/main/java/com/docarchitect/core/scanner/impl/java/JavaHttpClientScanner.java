package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for HTTP client relationships in Java source files.
 *
 * <p>This scanner detects service-to-service HTTP calls by analyzing:
 * <ul>
 *   <li>Spring Cloud Feign client declarations (@FeignClient)</li>
 *   <li>RestTemplate invocations (getForObject, postForObject, etc.)</li>
 *   <li>WebClient invocations (get(), post(), etc.)</li>
 * </ul>
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Java files using pattern matching</li>
 *   <li>Parse Java source using JavaParser AST</li>
 *   <li>Find Feign client interfaces with @FeignClient annotations</li>
 *   <li>Find RestTemplate and WebClient method calls in code</li>
 *   <li>Extract service names from URLs and Feign client names</li>
 *   <li>Create Relationship records with type CALLS</li>
 * </ol>
 *
 * <p><b>URL Pattern Examples:</b>
 * <pre>{@code
 * // Feign
 * @FeignClient(name = "user-service")
 * interface UserClient { }
 *
 * // RestTemplate
 * restTemplate.getForObject("http://order-service/api/orders", ...)
 * restTemplate.exchange("http://payment-service:8080/pay", ...)
 *
 * // WebClient
 * webClient.get().uri("http://catalog-api/products")
 * }</pre>
 *
 * @see AbstractJavaParserScanner
 * @see Relationship
 * @since 1.0.0
 */
public class JavaHttpClientScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "java-http-client";
    private static final String DISPLAY_NAME = "Java HTTP Client Scanner";
    private static final String JAVA_FILE_PATTERN = "**/*.java";
    private static final int DEFAULT_PRIORITY = 60;

    private static final String FEIGN_CLIENT_ANNOTATION = "FeignClient";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String VALUE_ATTRIBUTE = "value";
    private static final String URL_ATTRIBUTE = "url";

    // Patterns for RestTemplate method calls
    private static final Set<String> REST_TEMPLATE_METHODS = Set.of(
        "getForObject", "getForEntity",
        "postForObject", "postForEntity",
        "put", "exchange", "delete", "patchForObject"
    );

    // Pattern to extract service names from URLs
    // Matches: http://service-name, http://service-name:8080, http://service-name/path
    private static final Pattern SERVICE_URL_PATTERN = Pattern.compile(
        "https?://([a-zA-Z0-9-_.]+)(?::[0-9]+)?(?:/.*)?",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract service names from Feign configuration
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile(
        "[a-zA-Z0-9-_]+",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(JAVA_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, JAVA_FILE_PATTERN);
    }

    /**
     * Pre-filter files to only scan those containing HTTP client patterns.
     *
     * <p>This avoids attempting to parse files that don't contain HTTP client code,
     * reducing unnecessary processing and improving performance.
     *
     * <p><b>Detection Strategy:</b>
     * <ol>
     *   <li>Feign imports: org.springframework.cloud.openfeign.FeignClient</li>
     *   <li>RestTemplate usage: RestTemplate, getForObject, postForObject, etc.</li>
     *   <li>WebClient usage: WebClient, .get(), .post(), etc.</li>
     *   <li>Generic HTTP patterns: http://, https:// URLs in string literals</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains HTTP client patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);

            // Check for Feign imports and annotations
            boolean hasFeignPatterns =
                content.contains("FeignClient") ||
                content.contains("org.springframework.cloud.openfeign");

            // Check for RestTemplate usage
            boolean hasRestTemplatePatterns =
                content.contains("RestTemplate") ||
                content.contains("getForObject") ||
                content.contains("postForObject") ||
                content.contains("exchange(");

            // Check for WebClient usage
            boolean hasWebClientPatterns =
                content.contains("WebClient") ||
                content.contains("org.springframework.web.reactive.function.client");

            // Check for HTTP URLs (basic heuristic)
            boolean hasHttpUrls =
                content.contains("http://") || content.contains("https://");

            boolean hasHttpClientPatterns =
                hasFeignPatterns || hasRestTemplatePatterns || hasWebClientPatterns;

            if (hasHttpClientPatterns) {
                log.debug("Including file with HTTP client patterns: {} (feign={}, restTemplate={}, webClient={})",
                    file.getFileName(), hasFeignPatterns, hasRestTemplatePatterns, hasWebClientPatterns);
                return true;
            }

            // If no specific patterns but has HTTP URLs, still scan (could be string templates)
            if (hasHttpUrls) {
                log.trace("Including file with HTTP URLs: {}", file.getFileName());
                return true;
            }

            log.trace("Skipping file without HTTP client patterns: {}", file.getFileName());
            return false;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Java HTTP client relationships in: {}", context.rootPath());

        List<Relationship> relationships = new ArrayList<>();
        Set<String> processedRelationships = new HashSet<>();

        // Find all Java files
        List<Path> javaFiles = context.findFiles(JAVA_FILE_PATTERN).toList();

        if (javaFiles.isEmpty()) {
            log.warn("No Java files found in project");
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path javaFile : javaFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(javaFile)) {
                skippedFiles++;
                continue;
            }

            try {
                extractHttpClientRelationships(javaFile, relationships, processedRelationships, context);
                parsedFiles++;
            } catch (Exception e) {
                log.debug("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.debug("Pre-filtered {} files (not HTTP client code)", skippedFiles);
        log.info("Found {} HTTP client relationships across {} Java files (parsed {}/{})",
            relationships.size(), javaFiles.size(), parsedFiles, javaFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            relationships,
            List.of()  // No warnings
        );
    }

    /**
     * Extracts HTTP client relationships from a single Java file.
     *
     * @param javaFile path to Java file
     * @param relationships list to add discovered relationships
     * @param processedRelationships set of already processed relationship keys (for deduplication)
     * @param context scan context
     * @throws IOException if file cannot be read
     */
    private void extractHttpClientRelationships(
            Path javaFile,
            List<Relationship> relationships,
            Set<String> processedRelationships,
            ScanContext context) throws IOException {

        // Parse Java source using JavaParser
        Optional<CompilationUnit> compilationUnit = parseJavaFile(javaFile);

        if (compilationUnit.isEmpty()) {
            return; // Parsing failed, skip this file
        }

        CompilationUnit cu = compilationUnit.get();

        // Extract source component ID from package/class name
        String packageName = getPackageName(cu);
        String sourceComponentId = packageName.isEmpty() ? "default" : packageName.split("\\.")[0];

        // Find Feign client relationships
        extractFeignClientRelationships(cu, sourceComponentId, relationships, processedRelationships);

        // Find RestTemplate and WebClient relationships
        extractRestTemplateRelationships(cu, sourceComponentId, relationships, processedRelationships);
        extractWebClientRelationships(cu, sourceComponentId, relationships, processedRelationships);
    }

    /**
     * Extracts Feign client relationships from a compilation unit.
     *
     * @param cu compilation unit
     * @param sourceComponentId source component ID
     * @param relationships list to add discovered relationships
     * @param processedRelationships set of already processed relationship keys
     */
    private void extractFeignClientRelationships(
            CompilationUnit cu,
            String sourceComponentId,
            List<Relationship> relationships,
            Set<String> processedRelationships) {

        // Find all classes/interfaces
        List<ClassOrInterfaceDeclaration> classes = findClasses(cu);

        for (ClassOrInterfaceDeclaration clazz : classes) {
            // Check if class/interface has @FeignClient annotation
            if (!hasAnnotation(clazz, FEIGN_CLIENT_ANNOTATION)) {
                continue;
            }

            // Extract service name from @FeignClient annotation
            String serviceName = getAnnotationAttribute(clazz, FEIGN_CLIENT_ANNOTATION, NAME_ATTRIBUTE);
            if (serviceName == null || serviceName.isEmpty()) {
                serviceName = getAnnotationAttribute(clazz, FEIGN_CLIENT_ANNOTATION, VALUE_ATTRIBUTE);
            }

            // Also check for URL attribute (can contain service URL)
            String serviceUrl = getAnnotationAttribute(clazz, FEIGN_CLIENT_ANNOTATION, URL_ATTRIBUTE);

            String targetService = null;

            // Prefer service name from annotation
            if (serviceName != null && !serviceName.isEmpty()) {
                targetService = serviceName;
            } else if (serviceUrl != null && !serviceUrl.isEmpty()) {
                // Extract service name from URL
                targetService = extractServiceNameFromUrl(serviceUrl);
            }

            if (targetService != null && !targetService.isEmpty()) {
                String targetComponentId = IdGenerator.generate(targetService);
                addRelationship(
                    sourceComponentId,
                    targetComponentId,
                    "Feign client call to " + targetService,
                    "HTTP/Feign",
                    relationships,
                    processedRelationships
                );

                log.debug("Found Feign client: {} -> {}", sourceComponentId, targetService);
            }
        }
    }

    /**
     * Extracts RestTemplate relationships from a compilation unit.
     *
     * @param cu compilation unit
     * @param sourceComponentId source component ID
     * @param relationships list to add discovered relationships
     * @param processedRelationships set of already processed relationship keys
     */
    private void extractRestTemplateRelationships(
            CompilationUnit cu,
            String sourceComponentId,
            List<Relationship> relationships,
            Set<String> processedRelationships) {

        // Find all method calls in the compilation unit
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);

        for (MethodCallExpr methodCall : methodCalls) {
            String methodName = methodCall.getNameAsString();

            // Check if this is a RestTemplate method call
            if (!REST_TEMPLATE_METHODS.contains(methodName)) {
                continue;
            }

            // Extract URL from method arguments (usually first argument)
            if (methodCall.getArguments().isEmpty()) {
                continue;
            }

            String urlArgument = methodCall.getArguments().get(0).toString();
            String targetService = extractServiceNameFromUrl(urlArgument);

            if (targetService != null && !targetService.isEmpty()) {
                String targetComponentId = IdGenerator.generate(targetService);
                addRelationship(
                    sourceComponentId,
                    targetComponentId,
                    "RestTemplate call to " + targetService,
                    "HTTP/RestTemplate",
                    relationships,
                    processedRelationships
                );

                log.debug("Found RestTemplate call: {} -> {} ({})", sourceComponentId, targetService, methodName);
            }
        }
    }

    /**
     * Extracts WebClient relationships from a compilation unit.
     *
     * @param cu compilation unit
     * @param sourceComponentId source component ID
     * @param relationships list to add discovered relationships
     * @param processedRelationships set of already processed relationship keys
     */
    private void extractWebClientRelationships(
            CompilationUnit cu,
            String sourceComponentId,
            List<Relationship> relationships,
            Set<String> processedRelationships) {

        // Find all method calls in the compilation unit
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);

        for (MethodCallExpr methodCall : methodCalls) {
            String methodName = methodCall.getNameAsString();

            // Look for .uri() or .url() method calls specifically
            if (!methodName.equals("uri") && !methodName.equals("url")) {
                continue;
            }

            // Extract URL from the uri() or url() call
            if (methodCall.getArguments().isEmpty()) {
                continue;
            }

            String urlArgument = methodCall.getArguments().get(0).toString();
            String targetService = extractServiceNameFromUrl(urlArgument);

            if (targetService != null && !targetService.isEmpty()) {
                String targetComponentId = IdGenerator.generate(targetService);
                addRelationship(
                    sourceComponentId,
                    targetComponentId,
                    "WebClient call to " + targetService,
                    "HTTP/WebClient",
                    relationships,
                    processedRelationships
                );

                log.debug("Found WebClient call: {} -> {}", sourceComponentId, targetService);
            }
        }
    }


    /**
     * Extracts service name from a URL string.
     *
     * @param url URL string (can be a code expression like "http://service-name/path")
     * @return service name if found, null otherwise
     */
    private String extractServiceNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Clean up the URL string (remove quotes, etc.)
        String cleanedUrl = url.replaceAll("[\"']", "").trim();

        // Match against service URL pattern
        Matcher matcher = SERVICE_URL_PATTERN.matcher(cleanedUrl);
        if (matcher.find()) {
            String serviceName = matcher.group(1);

            // Filter out common non-service hostnames
            if (isServiceName(serviceName)) {
                return serviceName;
            }
        }

        return null;
    }

    /**
     * Checks if a hostname is likely a service name (not localhost, IP, etc.).
     *
     * @param hostname hostname to check
     * @return true if likely a service name
     */
    private boolean isServiceName(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }

        // Filter out localhost and common non-service names
        String lowerHostname = hostname.toLowerCase();
        if (lowerHostname.equals("localhost") ||
            lowerHostname.equals("127.0.0.1") ||
            lowerHostname.startsWith("192.168.") ||
            lowerHostname.startsWith("10.") ||
            lowerHostname.contains("example.com")) {
            return false;
        }

        // Check if it looks like a service name (alphanumeric with hyphens/underscores)
        return SERVICE_NAME_PATTERN.matcher(hostname).matches();
    }

    /**
     * Adds a relationship to the list if not already processed.
     *
     * @param sourceId source component ID
     * @param targetId target component ID
     * @param description relationship description
     * @param technology technology used
     * @param relationships list to add the relationship
     * @param processedRelationships set of already processed relationship keys
     */
    private void addRelationship(
            String sourceId,
            String targetId,
            String description,
            String technology,
            List<Relationship> relationships,
            Set<String> processedRelationships) {

        // Create unique key for deduplication
        String relationshipKey = sourceId + "->" + targetId + ":" + technology;

        if (!processedRelationships.contains(relationshipKey)) {
            relationships.add(new Relationship(
                sourceId,
                targetId,
                RelationshipType.CALLS,
                description,
                technology
            ));
            processedRelationships.add(relationshipKey);
        }
    }
}
