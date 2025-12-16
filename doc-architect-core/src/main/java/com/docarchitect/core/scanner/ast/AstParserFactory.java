package com.docarchitect.core.scanner.ast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docarchitect.core.util.Technologies;

/**
 * Factory for creating language-specific AST parsers.
 *
 * <p>This factory implements the Factory pattern with lazy initialization and caching.
 * Parser instances are created on-demand and reused across scanner invocations.
 *
 * <p><b>Supported Languages:</b></p>
 * <ul>
 *   <li>Python: {@link #getPythonParser()}</li>
 *   <li>.NET (C#): {@link #getDotNetParser()}</li>
 *   <li>JavaScript/TypeScript: {@link #getJavaScriptParser()}</li>
 *   <li>Go: {@link #getGoParser()}</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Get Python parser
 * AstParser<PythonAst.PythonClass> pythonParser = AstParserFactory.getPythonParser();
 * if (pythonParser.isAvailable()) {
 *     List<PythonAst.PythonClass> classes = pythonParser.parseFile(pythonFile);
 * }
 *
 * // Get C# parser
 * AstParser<DotNetAst.CSharpClass> dotNetParser = AstParserFactory.getDotNetParser();
 * List<DotNetAst.CSharpClass> classes = dotNetParser.parseFile(csFile);
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This factory is thread-safe. Parser instances are cached in a {@link ConcurrentHashMap}
 * and initialized only once per language.</p>
 *
 * @see AstParser
 * @see PythonAst
 * @see DotNetAst
 * @see JavaScriptAst
 * @see GoAst
 * @since 1.0.0
 */
public final class AstParserFactory {

    private static final Logger log = LoggerFactory.getLogger(AstParserFactory.class);

    // Cache of parser instances (one per language)
    private static final Map<String, AstParser<?>> parserCache = new ConcurrentHashMap<>();

    private AstParserFactory() {
        // Utility class - no instantiation
    }

    /**
     * Gets the Python AST parser.
     *
     * <p>Uses ANTLR Python3 grammar with graceful fallback to regex parsing.
     * Supports both Python 2.7 and Python 3.x.
     *
     * @return Python parser instance (cached, thread-safe)
     */
    @SuppressWarnings("unchecked")
    public static AstParser<PythonAst.PythonClass> getPythonParser() {
        return (AstParser<PythonAst.PythonClass>) parserCache.computeIfAbsent(Technologies.PYTHON, lang ->
            createParser("com.docarchitect.core.scanner.impl.python.util.PythonAstParserAdapter", "Python")
        );
    }

    /**
     * Gets the .NET (C#) AST parser.
     *
     * <p>Uses ANTLR C# grammar with graceful fallback to regex parsing.
     * Supports C# 7.0 through C# 12.0.
     *
     * @return .NET parser instance (cached, thread-safe)
     */
    @SuppressWarnings("unchecked")
    public static AstParser<DotNetAst.CSharpClass> getDotNetParser() {
        return (AstParser<DotNetAst.CSharpClass>) parserCache.computeIfAbsent(Technologies.DOTNET, lang ->
            createParser("com.docarchitect.core.scanner.impl.dotnet.util.CSharpAstParserAdapter", ".NET")
        );
    }

    /**
     * Gets the JavaScript/TypeScript AST parser.
     *
     * <p>Uses ANTLR JavaScript grammar with graceful fallback to regex parsing.
     * Supports ES5, ES6, ES2015+ and all TypeScript versions.
     *
     * @return JavaScript parser instance (cached, thread-safe)
     */
    @SuppressWarnings("unchecked")
    public static AstParser<JavaScriptAst.ExpressRoute> getJavaScriptParser() {
        return (AstParser<JavaScriptAst.ExpressRoute>) parserCache.computeIfAbsent(Technologies.JAVASCRIPT, lang ->
            createParser("com.docarchitect.core.scanner.impl.javascript.util.JavaScriptAstParserAdapter", "JavaScript")
        );
    }

    /**
     * Gets the Go AST parser.
     *
     * <p>Uses go/parser from Go standard library via process execution.
     * For go.mod files, use {@link com.docarchitect.core.scanner.impl.go.GoModScanner} instead
     * (regex-based parsing is appropriate for Go's simple module format).
     *
     * @return Go parser instance (cached, thread-safe)
     */
    @SuppressWarnings("unchecked")
    public static AstParser<GoAst.GoStruct> getGoParser() {
        return (AstParser<GoAst.GoStruct>) parserCache.computeIfAbsent(Technologies.GO, lang ->
            createParser("com.docarchitect.core.scanner.impl.go.util.GoAstParserAdapter", "Go")
        );
    }

    /**
     * Helper method to create parser instances via reflection.
     *
     * @param className fully qualified class name
     * @param displayName display name for logging
     * @return parser instance
     * @throws IllegalStateException if parser cannot be created
     */
    private static AstParser<?> createParser(String className, String displayName) {
        try {
            Class<?> implClass = Class.forName(className);
            AstParser<?> parser = (AstParser<?>) implClass.getDeclaredConstructor().newInstance();
            log.info("{} AST parser initialized successfully", displayName);
            return parser;
        } catch (ClassNotFoundException e) {
            log.warn("{} AST parser not available: {}", displayName, e.getMessage());
            throw new IllegalStateException(displayName + " AST parser not available", e);
        } catch (ReflectiveOperationException e) {
            log.error("{} AST parser failed to initialize", displayName, e);
            throw new IllegalStateException(displayName + " AST parser failed to initialize", e);
        }
    }

    /**
     * Clears the parser cache (useful for testing).
     *
     * <p><b>Warning:</b> This method is intended for testing only.
     * Do not call this in production code.
     */
    public static void clearCache() {
        parserCache.clear();
        log.debug("AST parser cache cleared");
    }

    /**
     * Checks if a parser for the given language is available.
     *
     * @param language language identifier ("python", "dotnet", "javascript", "go")
     * @return true if parser is available and initialized
     */
    public static boolean isAvailable(String language) {
        try {
            switch (language.toLowerCase()) {
                case Technologies.PYTHON:
                    return getPythonParser().isAvailable();
                case Technologies.DOTNET:
                case "csharp":
                    return getDotNetParser().isAvailable();
                case Technologies.JAVASCRIPT:
                case Technologies.TYPESCRIPT:
                    return getJavaScriptParser().isAvailable();
                case Technologies.GO:
                case Technologies.GOLANG:
                    return getGoParser().isAvailable();
                default:
                    return false;
            }
        } catch (Exception e) {
            log.debug("Parser not available for language: {}", language);
            return false;
        }
    }
}
