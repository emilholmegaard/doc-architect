package com.docarchitect.core.scanner.impl.ruby.util;

import com.docarchitect.parser.RubyLexer;
import com.docarchitect.parser.RubyParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing Ruby files using ANTLR-based AST parsing.
 *
 * <p>This parser uses ANTLR's Ruby grammar to parse Ruby source files,
 * particularly Rails controllers and routes files, to extract API endpoint information.
 *
 * @since 1.0.0
 */
public class RubyAstParser {

    private static final Logger log = LoggerFactory.getLogger(RubyAstParser.class);

    /**
     * Represents a parsed Ruby class.
     */
    public static class RubyClass {
        public String name;
        public String superclass;
        public List<RubyMethod> methods;
        public List<String> beforeActions;
        public int lineNumber;

        public RubyClass(String name, String superclass, List<RubyMethod> methods,
                        List<String> beforeActions, int lineNumber) {
            this.name = name;
            this.superclass = superclass;
            this.methods = new ArrayList<>(methods);
            this.beforeActions = new ArrayList<>(beforeActions);
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return "RubyClass{" +
                "name='" + name + '\'' +
                ", superclass='" + superclass + '\'' +
                ", methods=" + methods.size() +
                ", beforeActions=" + beforeActions +
                '}';
        }
    }

    /**
     * Represents a parsed Ruby method.
     */
    public static class RubyMethod {
        public String name;
        public List<String> params;
        public int lineNumber;

        public RubyMethod(String name, List<String> params, int lineNumber) {
            this.name = name;
            this.params = new ArrayList<>(params);
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return "RubyMethod{" +
                "name='" + name + '\'' +
                ", params=" + params +
                '}';
        }
    }

    /**
     * Parse a Ruby file and extract classes and methods.
     *
     * @param filePath Path to the Ruby file
     * @return List of parsed RubyClass objects
     */
    public static List<RubyClass> parseFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            return parseContent(content);
        } catch (IOException e) {
            log.warn("Failed to read Ruby file {}: {}", filePath, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse Ruby source code content and extract classes and methods.
     *
     * @param content Ruby source code
     * @return List of parsed RubyClass objects
     */
    public static List<RubyClass> parseContent(String content) {
        List<RubyClass> classes = new ArrayList<>();

        try {
            CharStream input = CharStreams.fromString(content);
            RubyLexer lexer = new RubyLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            RubyParser parser = new RubyParser(tokens);

            // Reduce error reporting noise
            parser.removeErrorListeners();
            lexer.removeErrorListeners();

            ParseTree tree = parser.program();

            // Extract classes using a custom visitor
            RubyClassExtractor extractor = new RubyClassExtractor();
            classes = extractor.visit(tree);

        } catch (Exception e) {
            log.debug("ANTLR parsing failed for Ruby content, error: {}", e.getMessage());
            // Fall back to regex-based parsing
            classes = parseWithRegex(content);
        }

        return classes;
    }

    /**
     * Fallback regex-based parsing when ANTLR fails.
     */
    private static List<RubyClass> parseWithRegex(String content) {
        List<RubyClass> classes = new ArrayList<>();

        String[] lines = content.split("\n");
        RubyClass currentClass = null;
        int lineNum = 0;

        for (String line : lines) {
            lineNum++;

            // Match class definitions: class MyController < ApplicationController
            if (line.trim().matches("^class\\s+[A-Z]\\w*.*")) {
                String className = line.replaceAll("^\\s*class\\s+([A-Z]\\w*).*", "$1");
                String superclass = "";
                if (line.contains("<")) {
                    superclass = line.replaceAll(".*<\\s*([A-Z]\\w*).*", "$1");
                }

                if (currentClass != null) {
                    classes.add(currentClass);
                }
                currentClass = new RubyClass(className, superclass, new ArrayList<>(), new ArrayList<>(), lineNum);
            }
            // Match method definitions: def index
            else if (line.trim().matches("^def\\s+\\w+.*") && currentClass != null) {
                String methodName = line.replaceAll("^\\s*def\\s+(\\w+).*", "$1");
                currentClass.methods.add(new RubyMethod(methodName, List.of(), lineNum));
            }
            // Match before_action calls
            else if (line.trim().matches("^before_action\\s+:.*") && currentClass != null) {
                String action = line.replaceAll("^\\s*before_action\\s+:(\\w+).*", "$1");
                currentClass.beforeActions.add(action);
            }
            // Match end
            else if (line.trim().equals("end") && currentClass != null) {
                // Could be end of class or method - we'll add class on next class or EOF
            }
        }

        // Add last class
        if (currentClass != null) {
            classes.add(currentClass);
        }

        return classes;
    }

    /**
     * Custom visitor to extract Ruby classes from the parse tree.
     */
    private static class RubyClassExtractor {

        private List<RubyClass> classes = new ArrayList<>();

        List<RubyClass> visit(ParseTree tree) {
            walkTree(tree);
            return classes;
        }

        private void walkTree(ParseTree node) {
            if (node == null) return;

            String nodeType = node.getClass().getSimpleName();

            // Look for ClassDefinitionContext nodes
            if (nodeType.contains("ClassDefinitionContext")) {
                RubyClass rubyClass = extractClassInfo(node);
                if (rubyClass != null) {
                    classes.add(rubyClass);
                }
            }

            // Recursively visit children
            for (int i = 0; i < node.getChildCount(); i++) {
                walkTree(node.getChild(i));
            }
        }

        private RubyClass extractClassInfo(ParseTree classNode) {
            try {
                String className = extractClassName(classNode);
                String superclass = extractSuperclass(classNode);
                int lineNumber = getLineNumber(classNode);
                List<RubyMethod> methods = extractMethods(classNode);
                List<String> beforeActions = extractBeforeActions(classNode);

                if (className != null && !className.isEmpty()) {
                    return new RubyClass(className, superclass, methods, beforeActions, lineNumber);
                }
            } catch (Exception e) {
                // Silently ignore parsing errors
            }
            return null;
        }

        private String extractClassName(ParseTree classNode) {
            // Look for className child
            for (int i = 0; i < classNode.getChildCount(); i++) {
                ParseTree child = classNode.getChild(i);
                if (child.getClass().getSimpleName().contains("ClassNameContext")) {
                    return child.getText();
                }
            }
            return null;
        }

        private String extractSuperclass(ParseTree classNode) {
            // Look for superclass child
            for (int i = 0; i < classNode.getChildCount(); i++) {
                ParseTree child = classNode.getChild(i);
                if (child.getClass().getSimpleName().contains("SuperclassContext")) {
                    return child.getText();
                }
            }
            return "";
        }

        private List<RubyMethod> extractMethods(ParseTree classNode) {
            List<RubyMethod> methods = new ArrayList<>();
            extractMethodsRecursive(classNode, methods);
            return methods;
        }

        private void extractMethodsRecursive(ParseTree node, List<RubyMethod> methods) {
            if (node == null) return;

            String nodeType = node.getClass().getSimpleName();

            // Look for MethodDefinitionContext nodes
            if (nodeType.contains("MethodDefinitionContext")) {
                String methodName = extractMethodName(node);
                int lineNumber = getLineNumber(node);
                if (methodName != null && !methodName.isEmpty()) {
                    methods.add(new RubyMethod(methodName, List.of(), lineNumber));
                }
                return; // Don't recurse into method bodies
            }

            // Recursively visit children (only in class body, not nested classes)
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                String childType = child.getClass().getSimpleName();
                // Skip nested class definitions
                if (!childType.contains("ClassDefinitionContext")) {
                    extractMethodsRecursive(child, methods);
                }
            }
        }

        private String extractMethodName(ParseTree methodNode) {
            // Look for methodName child
            for (int i = 0; i < methodNode.getChildCount(); i++) {
                ParseTree child = methodNode.getChild(i);
                if (child.getClass().getSimpleName().contains("MethodNameContext")) {
                    return child.getText();
                }
            }
            return null;
        }

        private List<String> extractBeforeActions(ParseTree classNode) {
            List<String> beforeActions = new ArrayList<>();
            extractBeforeActionsRecursive(classNode, beforeActions);
            return beforeActions;
        }

        private void extractBeforeActionsRecursive(ParseTree node, List<String> beforeActions) {
            if (node == null) return;

            String nodeType = node.getClass().getSimpleName();

            // Look for BeforeActionCallContext nodes
            if (nodeType.contains("BeforeActionCallContext")) {
                String action = node.getText();
                // Extract symbol name from "before_action :authenticate" -> "authenticate"
                action = action.replaceAll("before_action\\s*:?(\\w+).*", "$1");
                if (!action.isEmpty()) {
                    beforeActions.add(action);
                }
            }

            // Recursively visit children (only in class body, not nested classes)
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                String childType = child.getClass().getSimpleName();
                // Skip nested class definitions and method bodies
                if (!childType.contains("ClassDefinitionContext") &&
                    !childType.contains("MethodBodyContext")) {
                    extractBeforeActionsRecursive(child, beforeActions);
                }
            }
        }

        private int getLineNumber(ParseTree node) {
            if (node instanceof ParserRuleContext) {
                return ((ParserRuleContext) node).getStart().getLine();
            }
            return 0;
        }
    }
}
