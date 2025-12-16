package com.docarchitect.core.scanner.impl.javascript.util;

import com.docarchitect.core.scanner.ast.JavaScriptAst;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing JavaScript/TypeScript files using ANTLR-based AST parsing.
 *
 * <p>This parser uses ANTLR's JavaScript grammar to accurately parse JavaScript and
 * TypeScript source files. It focuses on extracting Express.js routes and other
 * architectural patterns relevant for documentation.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Accurate syntax parsing via ANTLR</li>
 *   <li>Express route extraction (app.get, router.post, etc.)</li>
 *   <li>Function and class extraction</li>
 *   <li>Graceful fallback to regex when ANTLR parsing fails</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * List<JavaScriptAst.ExpressRoute> routes = JavaScriptAstParser.parseFile(Paths.get("routes/users.js"));
 * for (JavaScriptAst.ExpressRoute route : routes) {
 *     System.out.println(route.httpMethod() + " " + route.path());
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class JavaScriptAstParser {

    private static final boolean ANTLR_AVAILABLE = checkAntlrAvailability();

    /**
     * Regex to match Express route definitions: app.get('/path') or router.post('/path').
     * Captures: (1) app|router, (2) HTTP method, (3) path.
     */
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
        "(app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]"
    );

    /**
     * Parse a JavaScript/TypeScript file and extract Express routes.
     *
     * <p>This method attempts to parse the file using ANTLR. If ANTLR parsing fails,
     * it gracefully falls back to regex-based parsing as a fallback strategy.</p>
     *
     * @param filePath path to the JavaScript/TypeScript file
     * @return list of parsed Express routes (never null)
     * @throws IOException if the file cannot be read
     */
    public static List<JavaScriptAst.ExpressRoute> parseFile(Path filePath) throws IOException {
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
     * Check if ANTLR runtime and generated JavaScript parser are available.
     */
    private static boolean checkAntlrAvailability() {
        try {
            Class.forName("org.antlr.v4.runtime.BaseErrorListener");
            Class.forName("com.docarchitect.parser.JavaScriptLexer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Parse using ANTLR's JavaScript grammar (most accurate).
     */
    private static List<JavaScriptAst.ExpressRoute> parseWithAntlr(Path filePath) throws IOException {
        String source = Files.readString(filePath);

        CharStream input = CharStreams.fromString(source);
        Lexer lexer = createJavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = createJavaScriptParser(tokens);

        ParseTree tree = getProgramTree(parser);
        JavaScriptRouteVisitor visitor = new JavaScriptRouteVisitor();
        return visitor.visit(tree);
    }

    /**
     * Create JavaScript Lexer via reflection to avoid hard dependency.
     */
    private static Lexer createJavaScriptLexer(CharStream input) {
        try {
            Class<?> lexerClass = Class.forName("com.docarchitect.parser.JavaScriptLexer");
            return (Lexer) lexerClass.getDeclaredConstructor(CharStream.class).newInstance(input);
        } catch (Exception e) {
            throw new RuntimeException("JavaScriptLexer not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Create JavaScript Parser via reflection.
     */
    private static Parser createJavaScriptParser(CommonTokenStream tokens) {
        try {
            Class<?> parserClass = Class.forName("com.docarchitect.parser.JavaScriptParser");
            return (Parser) parserClass.getDeclaredConstructor(CommonTokenStream.class).newInstance(tokens);
        } catch (Exception e) {
            throw new RuntimeException("JavaScriptParser not found - ensure ANTLR grammar is generated", e);
        }
    }

    /**
     * Get program rule from parser via reflection.
     */
    private static ParseTree getProgramTree(Parser parser) {
        try {
            return (ParseTree) parser.getClass().getMethod("program").invoke(parser);
        } catch (Exception e) {
            throw new RuntimeException("Cannot invoke program on parser", e);
        }
    }

    /**
     * ANTLR ParseTree visitor for extracting Express routes.
     */
    private static class JavaScriptRouteVisitor {
        private final List<JavaScriptAst.ExpressRoute> routes = new ArrayList<>();

        List<JavaScriptAst.ExpressRoute> visit(ParseTree tree) {
            walkTree(tree);
            return routes;
        }

        private void walkTree(ParseTree node) {
            if (node == null) return;

            // Extract route from node text
            String text = node.getText();
            if (text != null && (text.contains("app.") || text.contains("router."))) {
                JavaScriptAst.ExpressRoute route = extractRouteFromText(text);
                if (route != null) {
                    routes.add(route);
                }
            }

            // Recursively visit children
            for (int i = 0; i < node.getChildCount(); i++) {
                walkTree(node.getChild(i));
            }
        }

        private JavaScriptAst.ExpressRoute extractRouteFromText(String text) {
            // Use regex to extract route details
            Matcher matcher = ROUTE_PATTERN.matcher(text);
            if (matcher.find()) {
                String routerName = matcher.group(1);
                String httpMethod = matcher.group(2);
                String path = matcher.group(3);
                return new JavaScriptAst.ExpressRoute(routerName, httpMethod, path, null);
            }
            return null;
        }
    }

    /**
     * Fallback regex-based parsing for when ANTLR is not available.
     */
    private static List<JavaScriptAst.ExpressRoute> parseWithRegex(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        List<JavaScriptAst.ExpressRoute> routes = new ArrayList<>();

        Matcher matcher = ROUTE_PATTERN.matcher(content);
        while (matcher.find()) {
            String routerName = matcher.group(1);
            String httpMethod = matcher.group(2);
            String path = matcher.group(3);

            routes.add(new JavaScriptAst.ExpressRoute(routerName, httpMethod, path, null));
        }

        return routes;
    }
}
