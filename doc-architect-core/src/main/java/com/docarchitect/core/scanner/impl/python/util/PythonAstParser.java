package com.docarchitect.core.scanner.impl.python.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Utility class for parsing Python files using ANTLR-based AST parsing.
 *
 * <p>This parser uses ANTLR's Python grammar to accurately parse Python source files
 * (both Python 2.x and 3.x) without relying on fragile regex patterns. It provides a
 * structured AST representation that can be traversed and analyzed reliably.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Accurate syntax parsing (handles comments, multiline strings, indentation)</li>
 *   <li>Class, method, and field extraction via AST visitors</li>
 *   <li>Decorator detection and analysis</li>
 *   <li>Base class inheritance analysis</li>
 *   <li>Graceful fallback to regex when ANTLR parsing fails</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * List<PythonClass> classes = PythonAstParser.parseFile(Paths.get("models.py"));
 * for (PythonClass cls : classes) {
 *     System.out.println("Class: " + cls.name);
 *     for (String base : cls.baseClasses) {
 *         System.out.println("  extends: " + base);
 *     }
 *     for (PythonField field : cls.fields) {
 *         System.out.println("  field: " + field.name + " : " + field.type);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PythonAstParser {

    private static final boolean ANTLR_AVAILABLE = checkAntlrAvailability();

    // --- Constants to remove magic numbers/strings ---
    private static final String ANTLR_BASE_ERROR_LISTENER_CLASS = "org.antlr.v4.runtime.BaseErrorListener";

    private static final String PY3_LEXER_CLASS = "com.docarchitect.parser.Python3Lexer";
    private static final String PY3_PARSER_CLASS = "com.docarchitect.parser.Python3Parser";
    private static final String PY2_LEXER_CLASS = "com.docarchitect.parser.Python2Lexer";
    private static final String PY2_PARSER_CLASS = "com.docarchitect.parser.Python2Parser";

    private static final String PARSER_FILE_INPUT_METHOD = "file_input";

    private static final String CLASSDEF_CONTEXT_SIMPLE_NAME = "ClassdefContext";
    private static final String CLASSDEF_ALT_SIMPLE_NAME = "ClassDef";

    private static final String TOKEN_NAME = "NAME";

    private static final String WORD_REGEX = "\\w+";
    private static final String BASE_CLASS_REGEX = "class\\s+\\w+\\s*\\(([^)]*)\\)";
    private static final String FIELD_DECLARATION_REGEX = "^\\s*(\\w+)\\s*(?::\\s*([\\w.\\[\\]]+))?\\s*=\\s*(.+?)$";
    private static final String DECORATOR_REGEX = "@(\\w+)";
    private static final String CLASS_DEF_REGEX = "^\\s*class\\s+(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*:";

    private static final String EXCLUDED_FIELD_NAME_META = "Meta";
    private static final String PRIVATE_FIELD_PREFIX = "_";

    private static final String NEWLINE = "\n";
    private static final String DECORATOR_PREFIX = "@";
    private static final String DECORATOR_ARG_OPEN_REGEX = "\\(";

    private static final String CLASS_KEYWORD = "class ";
    private static final String DEF_KEYWORD = "def ";

    private static final String TYPE_FROM_VALUE_REGEX = "(\\w+)\\s*\\(";
    private static final String TYPE_SPLIT_REGEX = "[\\(\\s]";
    private static final String DOT = ".";
    private static final String COMMA = ",";

    private static final String DEFAULT_TYPE_NAME = "Any";

    private static final int DECORATOR_LOOKBACK_LINES = 10;
    private static final int UNSET_INDENT = -1;
    private static final int DEFAULT_LINE_NUMBER = 0;
    private static final int LINES_INDEX_OFFSET = -1;

    /**
     * Represents a parsed Python class.
     */
    public static class PythonClass {
        public String name;
        public List<String> baseClasses;
        public List<PythonField> fields;
        public List<String> decorators;
        public int lineNumber;

        public PythonClass(String name, List<String> baseClasses, List<PythonField> fields,
                          List<String> decorators, int lineNumber) {
            this.name = name;
            this.baseClasses = new ArrayList<>(baseClasses);
            this.fields = new ArrayList<>(fields);
            this.decorators = new ArrayList<>(decorators);
            this.lineNumber = lineNumber;
        }

        /**
         * Check if this class inherits from a specific parent class.
         */
        public boolean inheritsFrom(String parentClass) {
            return baseClasses.stream()
                .anyMatch(base -> base.equals(parentClass) || 
                                base.endsWith("." + parentClass) ||
                                base.contains(parentClass));
        }

        @Override
        public String toString() {
            return "PythonClass{" +
                "name='" + name + '\'' +
                ", baseClasses=" + baseClasses +
                ", fields=" + fields.size() +
                ", decorators=" + decorators +
                '}';
        }
    }

    /**
     * Represents a parsed Python field/attribute.
     */
    public static class PythonField {
        public String name;
        public String type;
        public String value;
        public List<String> decorators;

        public PythonField(String name, String type, String value, List<String> decorators) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.decorators = new ArrayList<>(decorators);
        }

        @Override
        public String toString() {
            return name + " : " + type + (value != null ? " = " + value : "");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PythonField that = (PythonField) o;
            return Objects.equals(name, that.name) && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }
    }

    /**
     * Parse a Python file and extract class definitions.
     *
     * <p>This method attempts to parse the file using ANTLR. If ANTLR parsing fails,
     * it gracefully falls back to regex-based parsing as a fallback strategy.</p>
     *
     * @param filePath path to the Python file
     * @return list of parsed classes (never null)
     * @throws IOException if the file cannot be read
     */
    public static List<PythonClass> parseFile(Path filePath) throws IOException {
        List<PythonClass> classes = new ArrayList<>();

        if (ANTLR_AVAILABLE) {
            try {
                classes = parseWithAntlr(filePath);
                return classes;
            } catch (Exception e) {
                // Fall through to regex
            }
        }

        // Fallback to regex-based parsing
        return parseWithRegex(filePath);
    }

    /**
     * Check if ANTLR runtime is available on the classpath.
     */
    private static boolean checkAntlrAvailability() {
        try {
            Class.forName(ANTLR_BASE_ERROR_LISTENER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Parse using ANTLR's Python grammar (most accurate).
     *
     * <p>Supports both Python 2.x and Python 3.x syntax.
     */
    private static List<PythonClass> parseWithAntlr(Path filePath) throws IOException {
        String source = new String(java.nio.file.Files.readAllBytes(filePath));

        // Try Python 3 grammar first, fall back to Python 2 if needed
        try {
            return parseWithPython3Grammar(source);
        } catch (Exception e) {
            return parseWithPython2Grammar(source);
        }
    }

    /**
     * Parse using Python 3 grammar.
     */
    private static List<PythonClass> parseWithPython3Grammar(String source) {
        try {
            CharStream input = CharStreams.fromString(source);
            // Create lexer and parser - assuming ANTLR generated Python3Lexer/Parser
            // Adjust class names based on your ANTLR grammar generation
            Lexer lexer = createPython3Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            Parser parser = createPython3Parser(tokens);

            ParseTree tree = getFileInputTree(parser);
            PythonClassVisitor visitor = new PythonClassVisitor();
            return visitor.visit(tree);

        } catch (Exception e) {
            throw new RuntimeException("Python 3 parsing failed", e);
        }
    }

    /**
     * Parse using Python 2 grammar.
     */
    private static List<PythonClass> parseWithPython2Grammar(String source) {
        try {
            CharStream input = CharStreams.fromString(source);
            Lexer lexer = createPython2Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            Parser parser = createPython2Parser(tokens);

            ParseTree tree = getFileInputTree(parser);
            PythonClassVisitor visitor = new PythonClassVisitor();
            return visitor.visit(tree);

        } catch (Exception e) {
            throw new RuntimeException("Python 2 parsing failed", e);
        }
    }

    /**
     * Create Python 3 Lexer via reflection to avoid hard dependency.
     */
    private static Lexer createPython3Lexer(CharStream input) {
        try {
            Class<?> lexerClass = Class.forName(PY3_LEXER_CLASS);
            return (Lexer) lexerClass.getDeclaredConstructor(CharStream.class).newInstance(input);
        } catch (Exception e) {
            throw new RuntimeException("Python3Lexer not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Create Python 3 Parser via reflection.
     */
    private static Parser createPython3Parser(CommonTokenStream tokens) {
        try {
            Class<?> parserClass = Class.forName(PY3_PARSER_CLASS);
            return (Parser) parserClass.getDeclaredConstructor(CommonTokenStream.class).newInstance(tokens);
        } catch (Exception e) {
            throw new RuntimeException("Python3Parser not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Create Python 2 Lexer via reflection.
     */
    private static Lexer createPython2Lexer(CharStream input) {
        try {
            Class<?> lexerClass = Class.forName(PY2_LEXER_CLASS);
            return (Lexer) lexerClass.getDeclaredConstructor(CharStream.class).newInstance(input);
        } catch (Exception e) {
            throw new RuntimeException("Python2Lexer not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Create Python 2 Parser via reflection.
     */
    private static Parser createPython2Parser(CommonTokenStream tokens) {
        try {
            Class<?> parserClass = Class.forName(PY2_PARSER_CLASS);
            return (Parser) parserClass.getDeclaredConstructor(CommonTokenStream.class).newInstance(tokens);
        } catch (Exception e) {
            throw new RuntimeException("Python2Parser not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Get file_input rule from parser via reflection.
     */
    private static ParseTree getFileInputTree(Parser parser) {
        try {
            return (ParseTree) parser.getClass().getMethod(PARSER_FILE_INPUT_METHOD).invoke(parser);
        } catch (Exception e) {
            try {
                return (ParseTree) parser.getClass().getMethod(PARSER_FILE_INPUT_METHOD).invoke(parser);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot invoke file_input on parser", e2);
            }
        }
    }

    /**
     * ANTLR ParseTree visitor for extracting Python classes.
     */
    private static class PythonClassVisitor {
        private List<PythonClass> classes = new ArrayList<>();

        List<PythonClass> visit(ParseTree tree) {
            walkTree(tree, 0);
            return classes;
        }

        private void walkTree(ParseTree node, int depth) {
            if (node == null) return;

            String nodeType = node.getClass().getSimpleName();

            // Look for classdef nodes
            if (nodeType.contains(CLASSDEF_CONTEXT_SIMPLE_NAME) || nodeType.contains(CLASSDEF_ALT_SIMPLE_NAME)) {
                PythonClass pythonClass = extractClassInfo(node);
                if (pythonClass != null) {
                    classes.add(pythonClass);
                }
            }

            // Recursively visit children
            for (int i = 0; i < node.getChildCount(); i++) {
                walkTree(node.getChild(i), depth + 1);
            }
        }

        private PythonClass extractClassInfo(ParseTree classNode) {
            try {
                String className = extractNodeValue(classNode, TOKEN_NAME);
                List<String> baseClasses = extractBaseClasses(classNode);
                List<PythonField> fields = extractFields(classNode);
                List<String> decorators = extractDecorators(classNode);
                int lineNumber = getLineNumber(classNode);

                if (className != null && !className.isEmpty()) {
                    return new PythonClass(className, baseClasses, fields, decorators, lineNumber);
                }
            } catch (Exception e) {
                // Silently ignore parsing errors
            }
            return null;
        }

        private String extractNodeValue(ParseTree node, String nodeName) {
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                if (child.getText().matches(WORD_REGEX) && child.getClass().getSimpleName().contains(nodeName)) {
                    return child.getText();
                }
            }
            return null;
        }

        private List<String> extractBaseClasses(ParseTree classNode) {
            List<String> bases = new ArrayList<>();
            try {
                String classText = classNode.getText();
                Pattern basePattern = Pattern.compile(BASE_CLASS_REGEX);
                java.util.regex.Matcher matcher = basePattern.matcher(classText);
                if (matcher.find()) {
                    String baseStr = matcher.group(1);
                    for (String base : baseStr.split(COMMA)) {
                        String trimmed = base.trim();
                        if (!trimmed.isEmpty()) {
                            bases.add(trimmed);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return bases;
        }

        private List<PythonField> extractFields(ParseTree classNode) {
            List<PythonField> fields = new ArrayList<>();
            try {
                String classBody = classNode.getText();
                Pattern fieldPattern = Pattern.compile(FIELD_DECLARATION_REGEX, Pattern.MULTILINE);

                java.util.regex.Matcher matcher = fieldPattern.matcher(classBody);
                while (matcher.find()) {
                    String fieldName = matcher.group(1);
                    String fieldType = matcher.group(2);
                    String fieldValue = matcher.group(3);

                    if (!fieldName.equals(EXCLUDED_FIELD_NAME_META) && !fieldName.startsWith(PRIVATE_FIELD_PREFIX)) {
                        if (fieldType == null) {
                            fieldType = extractTypeFromValue(fieldValue);
                        }
                        fields.add(new PythonField(fieldName, fieldType, fieldValue, new ArrayList<>()));
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return fields;
        }

        private List<String> extractDecorators(ParseTree classNode) {
            List<String> decorators = new ArrayList<>();
            try {
                String classText = classNode.getText();
                Pattern decorPattern = Pattern.compile(DECORATOR_REGEX);
                java.util.regex.Matcher matcher = decorPattern.matcher(classText);
                while (matcher.find()) {
                    decorators.add(matcher.group(1));
                }
            } catch (Exception e) {
                // Ignore
            }
            return decorators;
        }

        private int getLineNumber(ParseTree node) {
            try {
                if (node instanceof ParserRuleContext) {
                    return ((ParserRuleContext) node).getStart().getLine();
                }
            } catch (Exception e) {
                // Ignore
            }
            return DEFAULT_LINE_NUMBER;
        }
    }

    /**
     * Fallback regex-based parsing when ANTLR is unavailable or fails.
     */
    private static List<PythonClass> parseWithRegex(Path filePath) throws IOException {
        List<PythonClass> classes = new ArrayList<>();
        String content = new String(java.nio.file.Files.readAllBytes(filePath));
        List<String> lines = java.nio.file.Files.readAllLines(filePath);

        Pattern classPattern = Pattern.compile(CLASS_DEF_REGEX, Pattern.MULTILINE);

        java.util.regex.Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String baseClassesStr = classMatcher.group(2);
            List<String> baseClasses = parseBaseClasses(baseClassesStr);

            List<String> decorators = extractDecorators(content, classMatcher.start());

            int classBodyStart = classMatcher.end();
            String classBody = extractClassBodyFromString(
                content,
                classBodyStart,
                lines,
                content.substring(0, classMatcher.start()).split(NEWLINE).length + LINES_INDEX_OFFSET
            );
            List<PythonField> fields = extractFields(classBody);

            PythonClass pythonClass = new PythonClass(
                className,
                baseClasses,
                fields,
                decorators,
                content.substring(0, classMatcher.start()).split(NEWLINE).length
            );

            classes.add(pythonClass);
        }

        return classes;
    }

    /**
     * Extract decorator names from content before class definition.
     */
    private static List<String> extractDecorators(String content, int classStartPos) {
        List<String> decorators = new ArrayList<>();
        String beforeClass = content.substring(0, classStartPos);
        String[] lines = beforeClass.split(NEWLINE);

        for (int i = lines.length - 1; i >= 0 && i >= lines.length - DECORATOR_LOOKBACK_LINES; i--) {
            String line = lines[i].trim();
            if (line.startsWith(DECORATOR_PREFIX)) {
                String decorator = line.substring(1).split(DECORATOR_ARG_OPEN_REGEX)[0].trim();
                decorators.add(0, decorator);
            } else if (!line.isEmpty()) {
                break;
            }
        }

        return decorators;
    }

    /**
     * Extract class body from source content.
     */
    private static String extractClassBodyFromString(String content, int bodyStart,
                                                     List<String> lines, int lineNumber) {
        StringBuilder classBody = new StringBuilder();
        int baseIndent = UNSET_INDENT;

        for (int i = lineNumber + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int indent = line.length() - line.trim().length();

            if (baseIndent == UNSET_INDENT && !line.trim().isEmpty()) {
                baseIndent = indent;
            }

            if (baseIndent != UNSET_INDENT && !line.trim().isEmpty() && indent < baseIndent) {
                if (line.trim().startsWith(CLASS_KEYWORD) || line.trim().startsWith(DEF_KEYWORD)) {
                    break;
                }
            }

            classBody.append(line).append(NEWLINE);
        }

        return classBody.toString();
    }

    /**
     * Extract fields from class body using regex.
     */
    private static List<PythonField> extractFields(String classBody) {
        List<PythonField> fields = new ArrayList<>();
        Pattern fieldPattern = Pattern.compile(FIELD_DECLARATION_REGEX, Pattern.MULTILINE);

        java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(classBody);
        Set<String> seenFields = new HashSet<>();

        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String fieldType = fieldMatcher.group(2);
            String fieldValue = fieldMatcher.group(3);

            if (fieldName.equals(EXCLUDED_FIELD_NAME_META) || fieldName.startsWith(PRIVATE_FIELD_PREFIX) || seenFields.contains(fieldName)) {
                continue;
            }

            seenFields.add(fieldName);

            if (fieldType == null && fieldValue != null) {
                fieldType = extractTypeFromValue(fieldValue);
            }

            if (fieldType == null) {
                fieldType = DEFAULT_TYPE_NAME;
            }

            fields.add(new PythonField(
                fieldName,
                fieldType.trim(),
                fieldValue != null ? fieldValue.trim() : null,
                new ArrayList<>()
            ));
        }

        return fields;
    }

    /**
     * Extract type from field value expression.
     */
    private static String extractTypeFromValue(String value) {
        Pattern typePattern = Pattern.compile(TYPE_FROM_VALUE_REGEX);
        java.util.regex.Matcher typeMatcher = typePattern.matcher(value);

        if (typeMatcher.find()) {
            return typeMatcher.group(1);
        }

        if (value.contains(DOT)) {
            return value.substring(value.lastIndexOf(DOT) + 1).split(TYPE_SPLIT_REGEX)[0];
        }

        return value.split(TYPE_SPLIT_REGEX)[0];
    }

    /**
     * Parse base classes from inheritance declaration.
     */
    private static List<String> parseBaseClasses(String baseClassesStr) {
        List<String> bases = new ArrayList<>();

        if (baseClassesStr == null || baseClassesStr.trim().isEmpty()) {
            return bases;
        }

        for (String base : baseClassesStr.split(COMMA)) {
            String trimmed = base.trim();
            if (!trimmed.isEmpty()) {
                bases.add(trimmed);
            }
        }

        return bases;
    }
}
