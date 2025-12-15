package com.docarchitect.core.scanner.base;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for scanners that parse source code files using regular expressions.
 *
 * <p>This class is optimized for text-based pattern matching and provides:
 * <ul>
 *   <li>Precompiled regex pattern storage and matching</li>
 *   <li>Line-by-line and multi-line matching modes</li>
 *   <li>Match extraction with named groups</li>
 *   <li>File content utilities (lines, full content)</li>
 * </ul>
 *
 * <p>This base class is used by scanners for Python (FastAPI, Flask, SQLAlchemy, Django),
 * .NET (ASP.NET Core, Entity Framework), JavaScript (Express.js), Go, GraphQL, and SQL.
 *
 * <h3>When to Use This Base Class</h3>
 * <p>Use AbstractRegexScanner when:</p>
 * <ul>
 *   <li>Parsing languages without a Java AST parser (Python, JavaScript, C#, Go)</li>
 *   <li>Extracting specific patterns from text files (decorators, annotations, schema definitions)</li>
 *   <li>Performance is acceptable with line-by-line regex matching</li>
 *   <li>Full AST parsing would be overkill for the required information</li>
 * </ul>
 *
 * @see AbstractScanner
 * @since 1.0.0
 */
public abstract class AbstractRegexScanner extends AbstractScanner {

    /**
     * Constructor that initializes the logger from AbstractScanner.
     */
    protected AbstractRegexScanner() {
        super();
    }

    // ==================== Pattern Matching Utilities ====================

    /**
     * Finds all matches of a compiled pattern in the given text.
     *
     * @param pattern compiled regex pattern
     * @param text text to search
     * @return list of matchers (call .group() to extract matched text)
     */
    protected List<Matcher> findMatches(Pattern pattern, String text) {
        List<Matcher> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher);
        }
        return matches;
    }

    /**
     * Finds the first match of a pattern in the text.
     *
     * @param pattern compiled regex pattern
     * @param text text to search
     * @return matcher if found, null otherwise
     */
    protected Matcher findFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher : null;
    }

    /**
     * Checks if a pattern matches anywhere in the text.
     *
     * @param pattern compiled regex pattern
     * @param text text to search
     * @return true if pattern matches
     */
    protected boolean matches(Pattern pattern, String text) {
        return pattern.matcher(text).find();
    }

    /**
     * Extracts a named group from a matcher.
     *
     * @param matcher matcher with results
     * @param groupName name of the capture group
     * @return captured text, or null if group not found
     */
    protected String extractGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    /**
     * Extracts a numbered group from a matcher.
     *
     * @param matcher matcher with results
     * @param groupIndex index of the capture group (1-based)
     * @return captured text, or null if group not found
     */
    protected String extractGroup(Matcher matcher, int groupIndex) {
        try {
            return matcher.group(groupIndex);
        } catch (IndexOutOfBoundsException | IllegalStateException e) {
            return null;
        }
    }

    // ==================== Line-by-Line Processing ====================

    /**
     * Processes each line of a file with a pattern, collecting all matches.
     *
     * <p>This is more memory-efficient than loading the entire file for line-based patterns.
     *
     * @param file path to file
     * @param pattern compiled regex pattern
     * @return list of matchers, one per matching line
     * @throws IOException if file cannot be read
     */
    protected List<Matcher> findMatchesPerLine(Path file, Pattern pattern) throws IOException {
        List<Matcher> matches = new ArrayList<>();
        List<String> lines = readFileLines(file);

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                matches.add(matcher);
            }
        }

        return matches;
    }

    /**
     * Finds the first line in a file that matches the pattern.
     *
     * @param file path to file
     * @param pattern compiled regex pattern
     * @return matcher if found, null otherwise
     * @throws IOException if file cannot be read
     */
    protected Matcher findFirstLineMatch(Path file, Pattern pattern) throws IOException {
        List<String> lines = readFileLines(file);

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher;
            }
        }

        return null;
    }

    // ==================== Multi-Line Processing ====================

    /**
     * Finds all matches of a pattern across the entire file content.
     *
     * <p>Use this for patterns that may span multiple lines (e.g., multi-line comments, class definitions).
     *
     * @param file path to file
     * @param pattern compiled regex pattern (should use DOTALL flag for multi-line matching)
     * @return list of matchers
     * @throws IOException if file cannot be read
     */
    protected List<Matcher> findMatchesInFile(Path file, Pattern pattern) throws IOException {
        String content = readFileContent(file);
        return findMatches(pattern, content);
    }

    // ==================== String Utilities ====================

    /**
     * Safely extracts a substring from text, handling null and out-of-bounds cases.
     *
     * @param text source text
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return substring, or empty string if invalid
     */
    protected String safeSubstring(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length() || start >= end) {
            return "";
        }
        return text.substring(start, end);
    }

    /**
     * Trims whitespace and removes surrounding quotes from a string.
     *
     * <p>Useful for cleaning extracted string literals from source code.
     *
     * @param text text to clean
     * @return cleaned text
     */
    protected String cleanQuotes(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();

        // Remove surrounding single or double quotes
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }

        return trimmed;
    }

    /**
     * Checks if a line is a comment in common programming languages.
     *
     * <p>Recognizes common comment styles in Java, Python, SQL, and JavaScript.
     *
     * @param line line of code to check
     * @return true if line appears to be a comment
     */
    protected boolean isComment(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();
        return trimmed.startsWith("//")
            || trimmed.startsWith("#")
            || trimmed.startsWith("--")
            || trimmed.startsWith("/*")
            || trimmed.startsWith("*");
    }
}
