package com.docarchitect.core.scanner.impl.dotnet.util;

import com.docarchitect.core.scanner.ast.DotNetAst;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing C# files using ANTLR-based AST parsing.
 *
 * <p>This parser uses ANTLR's C# grammar to accurately parse C# source files
 * without relying on fragile regex patterns. It provides a structured AST
 * representation that can be traversed and analyzed reliably.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Accurate syntax parsing (handles comments, attributes, generics)</li>
 *   <li>Class, method, property, and field extraction via AST visitors</li>
 *   <li>Attribute detection and analysis</li>
 *   <li>Base class inheritance analysis</li>
 *   <li>Graceful fallback to regex when ANTLR parsing fails</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * List<DotNetAst.CSharpClass> classes = CSharpAstParser.parseFile(Paths.get("UserController.cs"));
 * for (DotNetAst.CSharpClass cls : classes) {
 *     System.out.println("Class: " + cls.name());
 *     for (String base : cls.baseClasses()) {
 *         System.out.println("  extends: " + base);
 *     }
 *     for (DotNetAst.Property prop : cls.properties()) {
 *         System.out.println("  property: " + prop.name() + " : " + prop.type());
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class CSharpAstParser {

    private static final boolean ANTLR_AVAILABLE = checkAntlrAvailability();

    /**
     * Parse a C# file and extract class definitions.
     *
     * <p>This method attempts to parse the file using ANTLR. If ANTLR parsing fails,
     * it gracefully falls back to regex-based parsing as a fallback strategy.</p>
     *
     * @param filePath path to the C# file
     * @return list of parsed classes (never null)
     * @throws IOException if the file cannot be read
     */
    public static List<DotNetAst.CSharpClass> parseFile(Path filePath) throws IOException {
        if (ANTLR_AVAILABLE) {
            try {
                return parseWithAntlr(filePath);
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
            Class.forName("com.docarchitect.parser.CSharpLexer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Parse using ANTLR's C# grammar (most accurate).
     */
    private static List<DotNetAst.CSharpClass> parseWithAntlr(Path filePath) throws IOException {
        String source = Files.readString(filePath);

        CharStream input = CharStreams.fromString(source);
        Lexer lexer = createCSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = createCSharpParser(tokens);

        ParseTree tree = getCompilationUnitTree(parser);
        CSharpClassVisitor visitor = new CSharpClassVisitor();
        return visitor.visit(tree);
    }

    /**
     * Create C# Lexer via reflection to avoid hard dependency.
     */
    private static Lexer createCSharpLexer(CharStream input) {
        try {
            Class<?> lexerClass = Class.forName("com.docarchitect.parser.CSharpLexer");
            return (Lexer) lexerClass.getDeclaredConstructor(CharStream.class).newInstance(input);
        } catch (Exception e) {
            throw new RuntimeException("CSharpLexer not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Create C# Parser via reflection.
     */
    private static Parser createCSharpParser(CommonTokenStream tokens) {
        try {
            Class<?> parserClass = Class.forName("com.docarchitect.parser.CSharpParser");
            return (Parser) parserClass.getDeclaredConstructor(CommonTokenStream.class).newInstance(tokens);
        } catch (Exception e) {
            throw new RuntimeException("CSharpParser not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Get compilation_unit rule from parser via reflection.
     */
    private static ParseTree getCompilationUnitTree(Parser parser) {
        try {
            return (ParseTree) parser.getClass().getMethod("compilation_unit").invoke(parser);
        } catch (Exception e) {
            throw new RuntimeException("Cannot invoke compilation_unit on parser", e);
        }
    }

    /**
     * ANTLR ParseTree visitor for extracting C# classes.
     */
    private static class CSharpClassVisitor {
        private final List<DotNetAst.CSharpClass> classes = new ArrayList<>();

        List<DotNetAst.CSharpClass> visit(ParseTree tree) {
            walkTree(tree);
            return classes;
        }

        private void walkTree(ParseTree node) {
            if (node == null) return;

            String nodeType = node.getClass().getSimpleName();

            // Look for class_definition nodes
            if (nodeType.contains("Class_definitionContext") || nodeType.contains("ClassDef")) {
                DotNetAst.CSharpClass csharpClass = extractClassInfo(node);
                if (csharpClass != null) {
                    classes.add(csharpClass);
                }
            }

            // Recursively visit children
            for (int i = 0; i < node.getChildCount(); i++) {
                walkTree(node.getChild(i));
            }
        }

        private DotNetAst.CSharpClass extractClassInfo(ParseTree classNode) {
            try {
                String className = extractClassName(classNode);
                if (className == null || className.isEmpty()) {
                    return null;
                }

                List<String> baseClasses = extractBaseClasses(classNode);
                List<DotNetAst.Attribute> attributes = extractAttributes(classNode);
                String namespace = ""; // Extract from parent context if needed

                // Extract class body
                List<DotNetAst.Property> properties = new ArrayList<>();
                List<DotNetAst.Method> methods = new ArrayList<>();
                extractClassMembers(classNode, properties, methods);

                return new DotNetAst.CSharpClass(
                    className,
                    baseClasses,
                    properties,
                    methods,
                    attributes,
                    namespace
                );
            } catch (Exception e) {
                // Silently ignore parsing errors
                return null;
            }
        }

        private String extractClassName(ParseTree node) {
            // Look for identifier token in class definition
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                String text = child.getText();
                if (text.matches("[A-Z]\\w+")) {
                    return text;
                }
            }
            return null;
        }

        private List<String> extractBaseClasses(ParseTree classNode) {
            List<String> baseClasses = new ArrayList<>();
            String nodeText = classNode.getText();

            // Simple extraction - look for : BaseClass pattern
            Pattern basePattern = Pattern.compile(":(\\w+)");
            Matcher matcher = basePattern.matcher(nodeText);
            if (matcher.find()) {
                baseClasses.add(matcher.group(1));
            }

            return baseClasses;
        }

        private List<DotNetAst.Attribute> extractAttributes(ParseTree node) {
            List<DotNetAst.Attribute> attributes = new ArrayList<>();
            // Extract attributes from parent nodes
            String text = node.getText();
            Pattern attrPattern = Pattern.compile("\\[(\\w+)(?:\\(([^)]*)\\))?\\]");
            Matcher matcher = attrPattern.matcher(text);
            while (matcher.find()) {
                String attrName = matcher.group(1);
                String args = matcher.group(2);
                List<String> argList = args != null ? List.of(args.split(",")) : List.of();
                attributes.add(new DotNetAst.Attribute(attrName, argList));
            }
            return attributes;
        }

        private void extractClassMembers(ParseTree node, List<DotNetAst.Property> properties,
                                        List<DotNetAst.Method> methods) {
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                String nodeType = child.getClass().getSimpleName();

                if (nodeType.contains("Property") || nodeType.contains("property")) {
                    DotNetAst.Property prop = extractProperty(child);
                    if (prop != null) {
                        properties.add(prop);
                    }
                } else if (nodeType.contains("Method") || nodeType.contains("method")) {
                    DotNetAst.Method method = extractMethod(child);
                    if (method != null) {
                        methods.add(method);
                    }
                }

                // Recurse for nested structures
                extractClassMembers(child, properties, methods);
            }
        }

        private DotNetAst.Property extractProperty(ParseTree node) {
            String text = node.getText();
            // Simple property extraction
            Pattern propPattern = Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\{");
            Matcher matcher = propPattern.matcher(text);
            if (matcher.find()) {
                String type = matcher.group(1);
                String name = matcher.group(2);
                boolean hasGetter = text.contains("get");
                boolean hasSetter = text.contains("set");
                return new DotNetAst.Property(name, type, hasGetter, hasSetter, List.of());
            }
            return null;
        }

        private DotNetAst.Method extractMethod(ParseTree node) {
            String text = node.getText();
            // Simple method extraction
            Pattern methodPattern = Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)");
            Matcher matcher = methodPattern.matcher(text);
            if (matcher.find()) {
                String returnType = matcher.group(1);
                String name = matcher.group(2);
                String params = matcher.group(3);
                return new DotNetAst.Method(name, returnType, List.of(), List.of());
            }
            return null;
        }
    }

    /**
     * Fallback regex-based parsing when ANTLR is unavailable or fails.
     */
    private static List<DotNetAst.CSharpClass> parseWithRegex(Path filePath) throws IOException {
        List<DotNetAst.CSharpClass> classes = new ArrayList<>();
        String content = Files.readString(filePath);

        // Pattern to match: public class ClassName : BaseClass (including partial classes)
        Pattern classPattern = Pattern.compile(
            "public\\s+(?:partial\\s+)?class\\s+(\\w+)(?:\\s*:\\s*(\\w+))?",
            Pattern.MULTILINE
        );

        Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String baseClass = classMatcher.group(2);
            List<String> baseClasses = baseClass != null ? List.of(baseClass) : List.of();

            // Extract properties and methods from class body
            int classStart = classMatcher.end();
            String classBody = extractClassBody(content, classStart);

            List<DotNetAst.Property> properties = extractPropertiesRegex(classBody);
            List<DotNetAst.Method> methods = extractMethodsRegex(classBody);
            List<DotNetAst.Attribute> attributes = extractAttributesRegex(content, classMatcher.start());

            DotNetAst.CSharpClass csharpClass = new DotNetAst.CSharpClass(
                className,
                baseClasses,
                properties,
                methods,
                attributes,
                ""
            );

            classes.add(csharpClass);
        }

        return classes;
    }

    private static String extractClassBody(String content, int start) {
        int braceCount = 0;
        boolean inClass = false;
        StringBuilder body = new StringBuilder();

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                braceCount++;
                inClass = true;
            } else if (c == '}') {
                braceCount--;
            }

            body.append(c);

            if (inClass && braceCount == 0) {
                break;
            }
        }

        return body.toString();
    }

    private static List<DotNetAst.Property> extractPropertiesRegex(String classBody) {
        List<DotNetAst.Property> properties = new ArrayList<>();
        Pattern propPattern = Pattern.compile(
            "public\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\{[^}]*get;[^}]*set;[^}]*\\}",
            Pattern.DOTALL
        );

        Matcher matcher = propPattern.matcher(classBody);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            properties.add(new DotNetAst.Property(name, type, true, true, List.of()));
        }

        return properties;
    }

    private static List<DotNetAst.Method> extractMethodsRegex(String classBody) {
        List<DotNetAst.Method> methods = new ArrayList<>();

        // Split by lines to better handle attributes
        String[] lines = classBody.split("\n");
        List<DotNetAst.Attribute> currentAttributes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Check if line contains an attribute
            if (line.startsWith("[") && line.contains("]")) {
                Pattern attrPattern = Pattern.compile("\\[(\\w+)(?:\\(([^)]*)\\))?\\]");
                Matcher attrMatcher = attrPattern.matcher(line);
                while (attrMatcher.find()) {
                    String attrName = attrMatcher.group(1);
                    String args = attrMatcher.group(2);
                    List<String> argList = args != null && !args.trim().isEmpty() ?
                        List.of(args) : List.of();
                    currentAttributes.add(new DotNetAst.Attribute(attrName, argList));
                }
                continue;
            }

            // Check if line contains a method declaration
            // Support: public [static] [async] [virtual|override] ReturnType MethodName(params)
            Pattern methodPattern = Pattern.compile(
                "public\\s+(?:static\\s+)?(?:async\\s+)?(?:virtual\\s+|override\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)"
            );
            Matcher matcher = methodPattern.matcher(line);
            if (matcher.find()) {
                String returnType = matcher.group(1);
                String name = matcher.group(2);
                String params = matcher.group(3);

                List<DotNetAst.Parameter> parameters = extractParameters(params);
                methods.add(new DotNetAst.Method(name, returnType, parameters, new ArrayList<>(currentAttributes)));
                currentAttributes.clear();
            } else if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("/*")) {
                // Non-empty, non-comment line that's not an attribute or method - clear attributes
                if (!line.startsWith("[")) {
                    currentAttributes.clear();
                }
            }
        }

        return methods;
    }

    private static List<DotNetAst.Parameter> extractParameters(String params) {
        List<DotNetAst.Parameter> parameters = new ArrayList<>();
        if (params == null || params.trim().isEmpty()) {
            return parameters;
        }

        // Handle parameters with attributes like [FromBody]
        Pattern paramWithAttrPattern = Pattern.compile("\\[From(\\w+)\\]\\s*(\\w+(?:<[^>]+>)?)\\s+(\\w+)");
        Pattern simpleParamPattern = Pattern.compile("(\\w+(?:<[^>]+>)?)\\s+(\\w+)");

        for (String param : params.split(",")) {
            param = param.trim();
            if (param.isEmpty()) continue;

            Matcher attrMatcher = paramWithAttrPattern.matcher(param);
            if (attrMatcher.find()) {
                String attrName = "From" + attrMatcher.group(1);
                String type = attrMatcher.group(2);
                String name = attrMatcher.group(3);
                List<DotNetAst.Attribute> attrs = List.of(new DotNetAst.Attribute(attrName, List.of()));
                parameters.add(new DotNetAst.Parameter(name, type, attrs));
            } else {
                Matcher simpleMatcher = simpleParamPattern.matcher(param);
                if (simpleMatcher.find()) {
                    String type = simpleMatcher.group(1);
                    String name = simpleMatcher.group(2);
                    parameters.add(new DotNetAst.Parameter(name, type, List.of()));
                }
            }
        }

        return parameters;
    }

    private static List<DotNetAst.Attribute> extractAttributesRegex(String content, int classStart) {
        List<DotNetAst.Attribute> attributes = new ArrayList<>();
        String beforeClass = content.substring(Math.max(0, classStart - 500), classStart);

        Pattern attrPattern = Pattern.compile("\\[(\\w+)(?:\\(([^)]*)\\))?\\]");
        Matcher matcher = attrPattern.matcher(beforeClass);
        while (matcher.find()) {
            String attrName = matcher.group(1);
            String args = matcher.group(2);
            List<String> argList = args != null && !args.trim().isEmpty() ?
                List.of(args.split(",")) : List.of();
            attributes.add(new DotNetAst.Attribute(attrName, argList));
        }

        return attributes;
    }
}
