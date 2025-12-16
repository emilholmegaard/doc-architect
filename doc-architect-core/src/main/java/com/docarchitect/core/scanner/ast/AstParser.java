package com.docarchitect.core.scanner.ast;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Common interface for language-specific AST parsers.
 *
 * <p>AST (Abstract Syntax Tree) parsers provide accurate, structured parsing of source code
 * without relying on fragile regex patterns. Each language has its own parser implementation
 * that handles language-specific syntax and semantics.
 *
 * <p><b>Supported Languages:</b></p>
 * <ul>
 *   <li>Python: {@link PythonAst} via ANTLR Python3 grammar (with regex fallback)</li>
 *   <li>.NET (C#): {@link DotNetAst} via Roslyn compiler API</li>
 *   <li>JavaScript/TypeScript: {@link JavaScriptAst} via Acorn/TypeScript parser</li>
 *   <li>Go: {@link GoAst} via go/parser (for .go source files)</li>
 * </ul>
 *
 * <p><b>Design Pattern:</b></p>
 * <p>This interface follows the Strategy pattern, allowing scanners to use different parsing
 * strategies based on the source language. The {@link AstParserFactory} provides language-specific
 * parser instances.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * AstParser<PythonAst.PythonClass> parser = AstParserFactory.getPythonParser();
 * List<PythonAst.PythonClass> classes = parser.parseFile(Paths.get("models.py"));
 *
 * for (PythonAst.PythonClass cls : classes) {
 *     System.out.println("Class: " + cls.name());
 *     for (PythonAst.Field field : cls.fields()) {
 *         System.out.println("  Field: " + field.name() + " : " + field.type());
 *     }
 * }
 * }</pre>
 *
 * @param <T> the AST node type returned by this parser
 * @see AstParserFactory
 * @see com.docarchitect.core.scanner.base.AbstractAstScanner
 * @since 1.0.0
 */
public interface AstParser<T> {

    /**
     * Parses a source file and returns a list of AST nodes.
     *
     * <p>The exact type of AST node depends on the language and parser implementation.
     * For example, Python parsers return {@link PythonAst.PythonClass} nodes, while
     * C# parsers return {@link DotNetAst.CSharpClass} nodes.
     *
     * @param filePath path to the source file to parse
     * @return list of AST nodes (typically class/interface/struct definitions)
     * @throws IOException if the file cannot be read
     * @throws AstParseException if parsing fails and no fallback is available
     */
    List<T> parseFile(Path filePath) throws IOException;

    /**
     * Checks if this parser is available (i.e., required dependencies are present).
     *
     * <p>Some parsers require external libraries (e.g., ANTLR runtime, Roslyn API).
     * This method checks if those dependencies are available on the classpath.
     *
     * @return true if parser is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Gets the language this parser supports.
     *
     * @return language identifier (e.g., "python", "csharp", "javascript")
     */
    String getLanguage();

    /**
     * Parses a string of source code (useful for testing).
     *
     * @param sourceCode source code to parse
     * @return list of AST nodes
     * @throws AstParseException if parsing fails
     */
    default List<T> parseString(String sourceCode) throws AstParseException {
        throw new UnsupportedOperationException(
            "parseString() not implemented for " + getLanguage() + " parser"
        );
    }

    /**
     * Exception thrown when AST parsing fails.
     */
    class AstParseException extends RuntimeException {
        public AstParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public AstParseException(String message) {
            super(message);
        }
    }
}
