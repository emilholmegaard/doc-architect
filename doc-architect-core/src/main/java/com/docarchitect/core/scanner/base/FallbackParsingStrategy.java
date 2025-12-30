package com.docarchitect.core.scanner.base;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy for parsing files when primary parsing (e.g., AST) fails.
 *
 * <p>This interface enables a three-tier parsing approach:
 * <ol>
 *   <li><b>Tier 1 (Primary):</b> Full AST parsing → HIGH confidence</li>
 *   <li><b>Tier 2 (Fallback):</b> Regex-based extraction → MEDIUM confidence</li>
 *   <li><b>Tier 3 (Metadata):</b> File/directory heuristics → LOW confidence</li>
 * </ol>
 *
 * <p>Implementing this strategy allows scanners to extract partial data
 * instead of returning zero results when AST parsing fails.
 *
 * <p><b>Design Principles (SOLID):</b></p>
 * <ul>
 *   <li><b>Single Responsibility:</b> Each strategy handles one parsing method</li>
 *   <li><b>Open/Closed:</b> New strategies can be added without modifying existing code</li>
 *   <li><b>Liskov Substitution:</b> All strategies are interchangeable</li>
 *   <li><b>Interface Segregation:</b> Simple, focused interface</li>
 *   <li><b>Dependency Inversion:</b> Scanners depend on abstraction, not concrete implementations</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public class SpringRestApiScanner extends AbstractJavaParserScanner {
 *
 *     @Override
 *     protected FallbackParsingStrategy<ApiEndpoint> createFallbackStrategy() {
 *         return (file, content) -> {
 *             List<ApiEndpoint> endpoints = new ArrayList<>();
 *
 *             // Regex pattern for @GetMapping("/path")
 *             Pattern pattern = Pattern.compile(
 *                 "@(Get|Post|Put|Delete|Patch)Mapping\\(\"([^\"]+)\"\\)"
 *             );
 *
 *             Matcher matcher = pattern.matcher(content);
 *             while (matcher.find()) {
 *                 String method = matcher.group(1).toUpperCase();
 *                 String path = matcher.group(2);
 *                 endpoints.add(new ApiEndpoint(..., path, method, ...));
 *             }
 *
 *             return endpoints;
 *         };
 *     }
 * }
 * }</pre>
 *
 * @param <T> type of result objects extracted (e.g., ApiEndpoint, DataEntity)
 * @since 1.0.0
 */
@FunctionalInterface
public interface FallbackParsingStrategy<T> {

    /**
     * Parses a file using fallback method (typically regex).
     *
     * <p>This method is called when primary parsing (AST) fails.
     * Implementations should be defensive and never throw exceptions.
     *
     * @param file path to the file being parsed
     * @param content file content as string
     * @return list of extracted objects (empty if nothing found, never null)
     */
    List<T> parse(Path file, String content);

    /**
     * Returns a no-op fallback strategy that always returns empty results.
     *
     * <p>Use this when no fallback parsing is possible or desired.
     *
     * @param <T> type of result objects
     * @return no-op strategy
     */
    static <T> FallbackParsingStrategy<T> noFallback() {
        return (file, content) -> List.of();
    }
}
