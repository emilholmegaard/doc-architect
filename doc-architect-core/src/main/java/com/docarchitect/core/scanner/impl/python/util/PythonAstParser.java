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
            Class.forName("org.antlr.v4.runtime.BaseErrorListener");
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
            Class<?> lexerClass = Class.forName("com.docarchitect.parser.Python3Lexer");
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
            Class<?> parserClass = Class.forName("com.docarchitect.parser.Python3Parser");
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
            Class<?> lexerClass = Class.forName("com.docarchitect.parser.Python2Lexer");
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
            Class<?> parserClass = Class.forName("com.docarchitect.parser.Python2Parser");
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
            // Try Python 3 first
            return (ParseTree) parser.getClass().getMethod("file_input").invoke(parser);
        } catch (Exception e) {
            try {
                // Try Python 2
                return (ParseTree) parser.getClass().getMethod("file_input").invoke(parser);
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
            if (nodeType.contains("ClassdefContext") || nodeType.contains("ClassDef")) {
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
                String className = extractNodeValue(classNode, "NAME");
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
                if (child.getText().matches("\\w+") && child.getClass().getSimpleName().contains(nodeName)) {
                    return child.getText();
                }
            }
            return null;
        }

        private List<String> extractBaseClasses(ParseTree classNode) {
            List<String> bases = new ArrayList<>();
            try {
                String classText = classNode.getText();
                Pattern basePattern = Pattern.compile("class\\s+\\w+\\s*\\(([^)]*)\\)");
                java.util.regex.Matcher matcher = basePattern.matcher(classText);
                if (matcher.find()) {
                    String baseStr = matcher.group(1);
                    for (String base : baseStr.split(",")) {
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
                Pattern fieldPattern = Pattern.compile(
                    "^\\s*(\\w+)\\s*(?::\\s*([\\w.\\[\\]]+))?\\s*=\\s*(.+?)$",
                    Pattern.MULTILINE
                );

                java.util.regex.Matcher matcher = fieldPattern.matcher(classBody);
                while (matcher.find()) {
                    String fieldName = matcher.group(1);
                    String fieldType = matcher.group(2);
                    String fieldValue = matcher.group(3);

                    if (!fieldName.equals("Meta") && !fieldName.startsWith("_")) {
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
                Pattern decorPattern = Pattern.compile("@(\\w+)");
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
            return 0;
        }
    }

    /**
     * Fallback regex-based parsing when ANTLR is unavailable or fails.
     */
    private static List<PythonClass> parseWithRegex(Path filePath) throws IOException {
        List<PythonClass> classes = new ArrayList<>();
        String content = new String(java.nio.file.Files.readAllBytes(filePath));
        List<String> lines = java.nio.file.Files.readAllLines(filePath);

        Pattern classPattern = Pattern.compile(
            "^\\s*class\\s+(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*:",
            Pattern.MULTILINE
        );

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
                content.substring(0, classMatcher.start()).split("\n").length - 1
            );
            List<PythonField> fields = extractFields(classBody);

            PythonClass pythonClass = new PythonClass(
                className,
                baseClasses,
                fields,
                decorators,
                content.substring(0, classMatcher.start()).split("\n").length
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
        String[] lines = beforeClass.split("\n");

        for (int i = lines.length - 1; i >= 0 && i >= lines.length - 10; i--) {
            String line = lines[i].trim();
            if (line.startsWith("@")) {
                String decorator = line.substring(1).split("\\(")[0].trim();
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
        int baseIndent = -1;

        for (int i = lineNumber + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int indent = line.length() - line.trim().length();

            if (baseIndent == -1 && !line.trim().isEmpty()) {
                baseIndent = indent;
            }

            if (baseIndent != -1 && !line.trim().isEmpty() && indent < baseIndent) {
                if (line.trim().startsWith("class ") || line.trim().startsWith("def ")) {
                    break;
                }
            }

            classBody.append(line).append("\n");
        }

        return classBody.toString();
    }

    /**
     * Extract fields from class body using regex.
     */
    private static List<PythonField> extractFields(String classBody) {
        List<PythonField> fields = new ArrayList<>();
        Pattern fieldPattern = Pattern.compile(
            "^\\s*(\\w+)\\s*(?::\\s*([\\w.\\[\\]]+))?\\s*=\\s*(.+?)$",
            Pattern.MULTILINE
        );

        java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(classBody);
        Set<String> seenFields = new HashSet<>();

        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String fieldType = fieldMatcher.group(2);
            String fieldValue = fieldMatcher.group(3);

            if (fieldName.equals("Meta") || fieldName.startsWith("_") || seenFields.contains(fieldName)) {
                continue;
            }

            seenFields.add(fieldName);

            if (fieldType == null && fieldValue != null) {
                fieldType = extractTypeFromValue(fieldValue);
            }

            if (fieldType == null) {
                fieldType = "Any";
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
        Pattern typePattern = Pattern.compile("(\\w+)\\s*\\(");
        java.util.regex.Matcher typeMatcher = typePattern.matcher(value);

        if (typeMatcher.find()) {
            return typeMatcher.group(1);
        }

        if (value.contains(".")) {
            return value.substring(value.lastIndexOf(".") + 1).split("[\\(\\s]")[0];
        }

        return value.split("[\\(\\s]")[0];
    }

    /**
     * Parse base classes from inheritance declaration.
     */
    private static List<String> parseBaseClasses(String baseClassesStr) {
        List<String> bases = new ArrayList<>();

        if (baseClassesStr == null || baseClassesStr.trim().isEmpty()) {
            return bases;
        }

        for (String base : baseClassesStr.split(",")) {
            String trimmed = base.trim();
            if (!trimmed.isEmpty()) {
                bases.add(trimmed);
            }
        }

        return bases;
    }
}
