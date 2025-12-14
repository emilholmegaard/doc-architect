package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Express.js REST endpoints in JavaScript and TypeScript source files.
 *
 * <p>This scanner uses regex patterns to extract Express.js route definitions from JavaScript/TypeScript files.
 * It identifies route registrations using both {@code app} and {@code router} objects.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate JavaScript and TypeScript files using pattern matching</li>
 *   <li>Parse source files as text using regex patterns (no AST parser needed)</li>
 *   <li>Find Express route definitions: {@code app.get()}, {@code router.post()}, etc.</li>
 *   <li>Extract HTTP method and path from each route</li>
 *   <li>Create ApiEndpoint records for each discovered endpoint</li>
 * </ol>
 *
 * <p><b>Supported Patterns:</b>
 * <ul>
 *   <li>{@code app.get('/users', handler)} - Express app routes</li>
 *   <li>{@code app.post('/users', handler)} - POST endpoints</li>
 *   <li>{@code router.put('/users/:id', handler)} - Express Router routes</li>
 *   <li>{@code router.delete('/users/:id', handler)} - DELETE endpoints</li>
 *   <li>{@code app.patch('/users/:id', handler)} - PATCH endpoints</li>
 * </ul>
 *
 * <p><b>Path Formats:</b>
 * <ul>
 *   <li>Static: {@code '/users'}</li>
 *   <li>Path parameters: {@code '/users/:id'}, {@code '/users/:userId/posts/:postId'}</li>
 *   <li>String literals: {@code '/api/v1/users'}</li>
 *   <li>Template literals (basic): {@code `/users/${version}`} (captured as-is)</li>
 * </ul>
 *
 * <p><b>Regex Pattern:</b>
 * <ul>
 *   <li>{@code ROUTE_PATTERN}: {@code (app|router)\.(get|post|put|delete|patch)\s*\(\s*['"`]([^'"`]+)['"`]}</li>
 *   <li>Captures: (1) app|router, (2) HTTP method, (3) path</li>
 * </ul>
 *
 * <p><b>Limitations:</b>
 * <ul>
 *   <li>Does not parse middleware chains or route handlers</li>
 *   <li>Does not extract parameter types or validation schemas</li>
 *   <li>Template literals with expressions are captured as-is</li>
 *   <li>Regex-based route patterns are captured but not interpreted</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new ExpressScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<ApiEndpoint> endpoints = result.apiEndpoints();
 * }</pre>
 *
 * @see Scanner
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class ExpressScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(ExpressScanner.class);

    /**
     * Regex to match Express route definitions: app.get('/path') or router.post('/path').
     * Captures: (1) app|router, (2) HTTP method, (3) path.
     *
     * <p>Supports single quotes, double quotes, and backticks (template literals).
     */
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
        "(app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]"
    );

    @Override
    public String getId() {
        return "express-api";
    }

    @Override
    public String getDisplayName() {
        return "Express.js API Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("javascript", "typescript");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.js", "**/*.ts");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Check if any JavaScript or TypeScript files exist
        return context.findFiles("**/*.js").findAny().isPresent()
            || context.findFiles("**/*.ts").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Express.js API endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();

        // Find all JavaScript and TypeScript files
        List<Path> jsFiles = context.findFiles("**/*.js").toList();
        List<Path> tsFiles = context.findFiles("**/*.ts").toList();

        List<Path> allFiles = new ArrayList<>();
        allFiles.addAll(jsFiles);
        allFiles.addAll(tsFiles);

        if (allFiles.isEmpty()) {
            log.warn("No JavaScript or TypeScript files found in project");
            return ScanResult.empty(getId());
        }

        int parsedFiles = 0;
        for (Path file : allFiles) {
            try {
                parseFile(file, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse file: {} - {}", file, e.getMessage());
                // Continue processing other files instead of failing completely
            }
        }

        log.info("Found {} Express.js API endpoints across {} files (parsed {}/{})",
            apiEndpoints.size(), allFiles.size(), parsedFiles, allFiles.size());

        return new ScanResult(
            getId(),
            true, // success
            List.of(), // No components
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of(), // No warnings
            List.of()  // No errors
        );
    }

    /**
     * Parses a single JavaScript or TypeScript file and extracts Express routes.
     *
     * @param file path to JS/TS file
     * @param apiEndpoints list to add discovered API endpoints
     * @throws IOException if file cannot be read
     */
    private void parseFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = Files.readString(file);
        String componentId = extractModuleName(file);

        // Find all route definitions
        Matcher matcher = ROUTE_PATTERN.matcher(content);

        while (matcher.find()) {
            String routerType = matcher.group(1); // app or router
            String method = matcher.group(2); // get, post, put, delete, patch
            String path = matcher.group(3);

            String httpMethod = method.toUpperCase();

            // Create API endpoint
            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                path,
                httpMethod,
                componentId + "." + routerType + "." + method,
                null, // Request schema not extracted
                null, // Response schema not extracted
                null  // Authentication not detected
            );

            apiEndpoints.add(endpoint);
            log.debug("Found Express.js endpoint: {} {} in {}", httpMethod, path, file.getFileName());
        }
    }

    /**
     * Extracts module name from file path.
     *
     * <p>Uses the file name without extension as the module identifier.
     *
     * @param file path to source file
     * @return module name
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        // Remove .js or .ts extension
        return fileName.replaceAll("\\.(js|ts)$", "");
    }
}
