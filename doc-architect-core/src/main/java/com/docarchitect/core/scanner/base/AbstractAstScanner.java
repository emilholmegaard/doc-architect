package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.ast.AstParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for scanners that use AST (Abstract Syntax Tree) parsing.
 *
 * <p>This class extends {@link AbstractScanner} and adds support for language-specific
 * AST parsers. Concrete scanners can use the {@link AstParser} interface to parse
 * source files with proper syntax understanding instead of fragile regex patterns.
 *
 * <p><b>Benefits of AST Parsing:</b></p>
 * <ul>
 *   <li>Accurate: Handles comments, multiline strings, indentation correctly</li>
 *   <li>Robust: Proper syntax tree traversal instead of text matching</li>
 *   <li>Maintainable: Centralized parsing logic in language-specific parsers</li>
 *   <li>Extensible: Easy to add new syntax elements without regex modifications</li>
 * </ul>
 *
 * <p><b>Supported Languages:</b></p>
 * <ul>
 *   <li>Python: ANTLR-based with regex fallback</li>
 *   <li>C#: Roslyn compiler API</li>
 *   <li>JavaScript/TypeScript: Acorn or TypeScript compiler</li>
 *   <li>Go: go/parser from standard library</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public class MyPythonScanner extends AbstractAstScanner<PythonAst.PythonClass> {
 *
 *     public MyPythonScanner() {
 *         super(AstParserFactory.getPythonParser());
 *     }
 *
 *     @Override
 *     public ScanResult scan(ScanContext context) {
 *         List<Path> pythonFiles = context.findFiles("**\/*.py").toList();
 *         List<DataEntity> entities = new ArrayList<>();
 *
 *         for (Path file : pythonFiles) {
 *             List<PythonAst.PythonClass> classes = parseAstFile(file);
 *             for (PythonAst.PythonClass cls : classes) {
 *                 // Process class AST node
 *                 entities.add(convertToDataEntity(cls));
 *             }
 *         }
 *
 *         return buildSuccessResult(entities, ...);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Comparison with Other Base Classes:</b></p>
 * <table border="1">
 *   <caption>Scanner Base Class Comparison</caption>
 *   <tr>
 *     <th>Base Class</th>
 *     <th>Parsing Strategy</th>
 *     <th>Best For</th>
 *   </tr>
 *   <tr>
 *     <td>AbstractRegexScanner</td>
 *     <td>Regex patterns</td>
 *     <td>Simple text formats (go.mod, .properties files)</td>
 *   </tr>
 *   <tr>
 *     <td>AbstractJacksonScanner</td>
 *     <td>Jackson (JSON/XML/YAML)</td>
 *     <td>Structured config files (pom.xml, package.json)</td>
 *   </tr>
 *   <tr>
 *     <td>AbstractJavaParserScanner</td>
 *     <td>JavaParser AST</td>
 *     <td>Java source files (.java)</td>
 *   </tr>
 *   <tr>
 *     <td><b>AbstractAstScanner</b></td>
 *     <td><b>Language-specific AST</b></td>
 *     <td><b>Python, C#, JavaScript, Go source files</b></td>
 *   </tr>
 * </table>
 *
 * @param <T> the AST node type returned by the parser
 * @see AbstractScanner
 * @see AstParser
 * @see com.docarchitect.core.scanner.ast.AstParserFactory
 * @since 1.0.0
 */
public abstract class AbstractAstScanner<T> extends AbstractScanner {

    /**
     * The AST parser instance for this scanner.
     */
    protected final AstParser<T> astParser;

    /**
     * Creates an AST scanner with the specified parser.
     *
     * @param astParser AST parser to use for parsing source files
     */
    protected AbstractAstScanner(AstParser<T> astParser) {
        super();
        this.astParser = astParser;
    }

    /**
     * Determines if a file should be scanned by this scanner.
     *
     * <p>This pre-filtering hook allows scanners to check file content before
     * attempting expensive AST parsing. Override this method to implement
     * framework-specific detection (e.g., checking for specific imports).
     *
     * <p><b>Default Implementation:</b> Returns {@code true} for all files.
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * @Override
     * protected boolean shouldScanFile(Path file) {
     *     try {
     *         String content = readFileContent(file);
     *         // Skip Django files when scanning for SQLAlchemy
     *         if (content.contains("from django.db import models")) {
     *             return false;
     *         }
     *         // Only scan files with SQLAlchemy imports
     *         return content.contains("from sqlalchemy import") ||
     *                content.contains("Base = declarative_base()");
     *     } catch (IOException e) {
     *         return false;
     *     }
     * }
     * }</pre>
     *
     * @param file path to the file to check
     * @return true if this file should be parsed, false to skip
     */
    protected boolean shouldScanFile(Path file) {
        return true; // Default: scan all files
    }

    /**
     * Parses a source file and returns AST nodes.
     *
     * <p>This method wraps {@link AstParser#parseFile(Path)} and handles
     * exceptions gracefully by logging warnings and returning an empty list.
     *
     * <p><b>Pre-filtering:</b> Calls {@link #shouldScanFile(Path)} before parsing.
     * If the file should not be scanned, returns an empty list without attempting to parse.
     *
     * <p><b>Error Handling:</b> Different exception types are logged at appropriate levels:
     * <ul>
     *   <li>IOException: WARN - File read errors</li>
     *   <li>AstParseException: DEBUG - Parser couldn't handle the file (may not match scanner's patterns)</li>
     *   <li>ArrayIndexOutOfBoundsException: DEBUG - Parser error on unsupported patterns</li>
     *   <li>Other exceptions: ERROR - Unexpected failures</li>
     * </ul>
     *
     * @param filePath path to the source file
     * @return list of AST nodes (empty if parsing fails or file should be skipped)
     */
    protected List<T> parseAstFile(Path filePath) {
        // Layer 1: Pre-filtering
        if (!shouldScanFile(filePath)) {
            log.debug("Skipping file (pre-filter): {}", filePath);
            return new ArrayList<>();
        }

        // Layer 2: Parse with graceful error handling
        try {
            return astParser.parseFile(filePath);
        } catch (IOException e) {
            log.warn("Failed to read file for AST parsing: {} - {}", filePath, e.getMessage());
            return new ArrayList<>();
        } catch (AstParser.AstParseException e) {
            // Parser couldn't handle this file - likely doesn't match our patterns
            log.debug("AST parsing skipped (unsupported pattern): {} - {}", filePath, e.getMessage());
            return new ArrayList<>();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Common parser error when file doesn't match expected structure
            log.debug("AST parsing skipped (parser error): {} - {}", filePath, e.getClass().getSimpleName());
            return new ArrayList<>();
        } catch (Exception e) {
            // Truly unexpected errors
            log.error("Unexpected error during AST parsing: {}", filePath, e);
            return new ArrayList<>();
        }
    }

    /**
     * Checks if the AST parser is available.
     *
     * <p>Some parsers require external libraries (e.g., ANTLR runtime) which
     * may not be present. Scanners should check parser availability before use.
     *
     * @return true if parser is available
     */
    protected boolean isParserAvailable() {
        return astParser.isAvailable();
    }

    /**
     * Gets the language supported by this scanner's AST parser.
     *
     * @return language identifier (e.g., "python", "csharp", "javascript")
     */
    protected String getParserLanguage() {
        return astParser.getLanguage();
    }
}
