package com.docarchitect.core.scanner.impl.javascript;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.JavaScriptAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Express.js REST endpoints in JavaScript and TypeScript source files.
 *
 * <p>This scanner uses ANTLR-based AST parsing to extract Express.js route definitions from JavaScript/TypeScript files.
 * It identifies route registrations using both {@code app} and {@code router} objects.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate JavaScript and TypeScript files using pattern matching</li>
 *   <li>Parse source files using ANTLR JavaScript grammar (with regex fallback)</li>
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
public class ExpressScanner extends AbstractAstScanner<JavaScriptAst.ExpressRoute> {

    public ExpressScanner() {
        super(AstParserFactory.getJavaScriptParser());
    }

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
        return Set.of(Technologies.JAVASCRIPT, Technologies.TYPESCRIPT);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("*.js", "**/*.js", "*.ts", "**/*.ts");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Check if any JavaScript or TypeScript files exist (both root and subdirectories)
        return hasAnyFiles(context, "*.js", "**/*.js", "*.ts", "**/*.ts");
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
            return emptyResult();
        }

        int parsedFiles = 0;
        for (Path file : allFiles) {
            try {
                parseJavaScriptFile(file, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse file: {} - {}", file, e.getMessage());
                // Continue processing other files instead of failing completely
            }
        }

        log.info("Found {} Express.js API endpoints across {} files (parsed {}/{})",
            apiEndpoints.size(), allFiles.size(), parsedFiles, allFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single JavaScript or TypeScript file and extracts Express routes using AST.
     *
     * @param file path to JS/TS file
     * @param apiEndpoints list to add discovered API endpoints
     * @throws IOException if file cannot be read
     */
    private void parseJavaScriptFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String componentId = extractModuleName(file);

        // Use AST parser to parse JavaScript file
        List<JavaScriptAst.ExpressRoute> routes = parseAstFile(file);

        for (JavaScriptAst.ExpressRoute route : routes) {
            String httpMethod = route.httpMethod().toUpperCase();
            String path = route.path();
            String routerName = route.routerName();

            // Create API endpoint
            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                path,
                httpMethod,
                componentId + "." + routerName + "." + route.httpMethod(),
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
