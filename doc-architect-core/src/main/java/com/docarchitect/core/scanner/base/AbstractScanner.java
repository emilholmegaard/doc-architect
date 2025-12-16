package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstract base class for scanner implementations providing common functionality.
 *
 * <p>This class reduces code duplication across scanner implementations by providing:
 * <ul>
 *   <li>Logger initialization (one logger per scanner class)</li>
 *   <li>File reading utilities ({@link #readFileContent(Path)}, {@link #readFileLines(Path)})</li>
 *   <li>appliesTo() helper ({@link #hasAnyFiles(ScanContext, String...)})</li>
 *   <li>ScanResult creation helpers ({@link #emptyResult()}, {@link #failedResult(List)})</li>
 * </ul>
 *
 * <p><b>Usage Example</b></p>
 * <p>Concrete scanners typically implement the pattern:</p>
 * <ol>
 *   <li>Override getId() to return a unique scanner identifier</li>
 *   <li>Use hasAnyFiles() in appliesTo() to check for relevant file patterns</li>
 *   <li>In scan(), use context.findFiles() to discover files matching glob patterns</li>
 *   <li>Return emptyResult() if no files found, or buildSuccessResult() with extracted data</li>
 * </ol>
 *
 * @see Scanner
 * @see ScanContext
 * @see ScanResult
 * @since 1.0.0
 */
public abstract class AbstractScanner implements Scanner {

    /**
     * Logger instance for this scanner.
     * Automatically initialized with the concrete scanner class name.
     */
    protected final Logger log;

    /**
     * Constructor that initializes the logger for the concrete scanner class.
     */
    protected AbstractScanner() {
        this.log = LoggerFactory.getLogger(getClass());
    }

    // ==================== File Reading Utilities ====================

    /**
     * Reads the entire content of a file as a single string.
     *
     * @param file path to the file to read
     * @return file content as string
     * @throws IOException if file cannot be read
     */
    protected String readFileContent(Path file) throws IOException {
        return Files.readString(file);
    }

    /**
     * Reads all lines from a file.
     *
     * @param file path to the file to read
     * @return list of lines
     * @throws IOException if file cannot be read
     */
    protected List<String> readFileLines(Path file) throws IOException {
        return Files.readAllLines(file);
    }

    // ==================== appliesTo() Helper ====================

    /**
     * Checks if any files matching the given glob patterns exist in the scan context.
     *
     * <p>This is a convenience method for implementing {@link #appliesTo(ScanContext)}.
     * It returns {@code true} if at least one file matching any of the patterns exists.
     *
     * @param context scan context
     * @param patterns glob patterns to check
     * @return true if at least one matching file exists
     */
    protected boolean hasAnyFiles(ScanContext context, String... patterns) {
        for (String pattern : patterns) {
            if (context.findFiles(pattern).findAny().isPresent()) {
                return true;
            }
        }
        return false;
    }

    // ==================== ScanResult Creation Helpers ====================

    /**
     * Creates an empty ScanResult for this scanner.
     *
     * <p>Use this when no relevant files are found or the scanner determines
     * it has nothing to report.
     *
     * @return empty ScanResult with this scanner's ID
     */
    protected ScanResult emptyResult() {
        return ScanResult.empty(getId());
    }

    /**
     * Creates a failed ScanResult for this scanner with error messages.
     *
     * <p>Use this when the scanner encounters an unrecoverable error during scanning.
     *
     * @param errors list of error messages
     * @return failed ScanResult with this scanner's ID and errors
     */
    protected ScanResult failedResult(List<String> errors) {
        return ScanResult.failed(getId(), errors);
    }

    /**
     * Creates a successful ScanResult with the given data.
     *
     * <p>This is a convenience method that constructs a ScanResult with success=true
     * and the provided data. Empty lists are provided for unused categories.
     *
     * @param components list of components (can be empty)
     * @param dependencies list of dependencies (can be empty)
     * @param apiEndpoints list of API endpoints (can be empty)
     * @param messageFlows list of message flows (can be empty)
     * @param dataEntities list of data entities (can be empty)
     * @param relationships list of relationships (can be empty)
     * @param warnings list of warning messages (can be empty)
     * @return successful ScanResult
     */
    @SuppressWarnings("unchecked")
    protected ScanResult buildSuccessResult(
            List<?> components,
            List<?> dependencies,
            List<?> apiEndpoints,
            List<?> messageFlows,
            List<?> dataEntities,
            List<?> relationships,
            List<String> warnings) {
        return new ScanResult(
            getId(),
            true, // success
            (List) components,
            (List) dependencies,
            (List) apiEndpoints,
            (List) messageFlows,
            (List) dataEntities,
            (List) relationships,
            warnings,
            List.of() // No errors for successful results
        );
    }
}
