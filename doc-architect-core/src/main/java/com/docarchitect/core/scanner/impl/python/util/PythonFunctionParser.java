package com.docarchitect.core.scanner.impl.python.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.scanner.ast.PythonAst;

/**
 * Utility class for parsing Python module-level functions using regex patterns.
 *
 * <p>This parser extracts function definitions with their decorators from Python source files.
 * It provides a simple regex-based approach with plans to migrate to ANTLR-based AST parsing
 * in the future for more robust handling of complex Python syntax.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Parses module-level function definitions</li>
 *   <li>Extracts function decorators with parameters</li>
 *   <li>Handles both sync and async functions</li>
 *   <li>Detects function invocations</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * List<PythonAst.Function> functions = PythonFunctionParser.parseFunctions(pythonFile);
 * for (PythonAst.Function func : functions) {
 *     if (func.hasDecorator("shared_task")) {
 *         System.out.println("Found Celery task: " + func.name());
 *     }
 * }
 *
 * List<PythonAst.FunctionCall> calls = PythonFunctionParser.parseFunctionCalls(pythonFile);
 * }</pre>
 *
 * @see PythonAst.Function
 * @see PythonAst.FunctionCall
 * @since 1.0.0
 */
public final class PythonFunctionParser {

    private PythonFunctionParser() {
        // Utility class - no instantiation
    }

    // Regex patterns for parsing
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "^\\s*@(.+?)\\s*$"
    );

    private static final Pattern FUNCTION_DEF_PATTERN = Pattern.compile(
        "^\\s*(async\\s+)?def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)"
    );

    private static final Pattern FUNCTION_CALL_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*)\\.(delay|apply_async)\\s*\\(([^)]*)\\)"
    );

    /**
     * Parse all module-level functions from a Python file.
     *
     * @param filePath path to the Python file
     * @return list of parsed functions (never null)
     * @throws IOException if file cannot be read
     */
    public static List<PythonAst.Function> parseFunctions(Path filePath) throws IOException {
        List<PythonAst.Function> functions = new ArrayList<>();
        List<String> lines = Files.readAllLines(filePath);

        List<String> currentDecorators = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // Check for decorator
            Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(trimmed);
            if (decoratorMatcher.matches()) {
                String decorator = decoratorMatcher.group(1);
                currentDecorators.add(decorator);
                continue;
            }

            // Check for function definition
            Matcher funcMatcher = FUNCTION_DEF_PATTERN.matcher(trimmed);
            if (funcMatcher.find()) {
                boolean isAsync = funcMatcher.group(1) != null;
                String functionName = funcMatcher.group(2);
                String params = funcMatcher.group(3);

                List<String> parameters = parseParameters(params);

                PythonAst.Function function = new PythonAst.Function(
                    functionName,
                    parameters,
                    new ArrayList<>(currentDecorators),
                    i + 1, // 1-indexed line number
                    isAsync
                );

                functions.add(function);
                currentDecorators.clear();
                continue;
            }

            // If we hit a non-decorator, non-function line, clear decorators
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                currentDecorators.clear();
            }
        }

        return functions;
    }

    /**
     * Parse all function calls from a Python file.
     *
     * @param filePath path to the Python file
     * @return list of parsed function calls (never null)
     * @throws IOException if file cannot be read
     */
    public static List<PythonAst.FunctionCall> parseFunctionCalls(Path filePath) throws IOException {
        List<PythonAst.FunctionCall> calls = new ArrayList<>();
        List<String> lines = Files.readAllLines(filePath);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            Matcher matcher = FUNCTION_CALL_PATTERN.matcher(line);
            while (matcher.find()) {
                String functionName = matcher.group(1);
                String method = matcher.group(2);
                String arguments = matcher.group(3);

                PythonAst.FunctionCall call = new PythonAst.FunctionCall(
                    functionName,
                    method,
                    arguments,
                    i + 1 // 1-indexed line number
                );

                calls.add(call);
            }
        }

        return calls;
    }

    /**
     * Parse function parameters from parameter string.
     *
     * <p>Handles simple parameter lists. Complex cases (nested defaults, annotations)
     * may not be fully parsed but this is sufficient for most use cases.
     *
     * @param paramString parameter string from function definition
     * @return list of parameter names
     */
    private static List<String> parseParameters(String paramString) {
        List<String> parameters = new ArrayList<>();

        if (paramString == null || paramString.trim().isEmpty()) {
            return parameters;
        }

        String[] parts = paramString.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Extract just the parameter name (before : or =)
            int colonIndex = trimmed.indexOf(':');
            int equalsIndex = trimmed.indexOf('=');

            int endIndex = trimmed.length();
            if (colonIndex > 0) {
                endIndex = Math.min(endIndex, colonIndex);
            }
            if (equalsIndex > 0) {
                endIndex = Math.min(endIndex, equalsIndex);
            }

            String paramName = trimmed.substring(0, endIndex).trim();
            if (!paramName.isEmpty()) {
                parameters.add(paramName);
            }
        }

        return parameters;
    }

    /**
     * Extract a parameter value from decorator or function call arguments.
     *
     * <p>Example:
     * <pre>{@code
     * extractParameter("queue='emails', name='task'", "queue") → "emails"
     * extractParameter("args=[1,2], queue='priority'", "queue") → "priority"
     * }</pre>
     *
     * @param argumentString full argument string
     * @param paramName parameter name to extract
     * @return parameter value or null if not found
     */
    public static String extractParameter(String argumentString, String paramName) {
        if (argumentString == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(paramName + "\\s*=\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(argumentString);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
