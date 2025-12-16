package com.docarchitect.core.scanner.impl.go.util;

import com.docarchitect.core.scanner.ast.GoAst;
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
 * Utility class for parsing Go files using ANTLR-based AST parsing.
 *
 * <p>This parser uses ANTLR's Go grammar to accurately parse Go source files.
 * It focuses on extracting struct definitions which are the primary architectural
 * elements in Go.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Accurate syntax parsing via ANTLR</li>
 *   <li>Struct definition extraction</li>
 *   <li>Graceful fallback to regex when ANTLR parsing fails</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * List<GoAst.GoStruct> structs = GoAstParser.parseFile(Paths.get("user.go"));
 * for (GoAst.GoStruct struct : structs) {
 *     System.out.println("Struct: " + struct.name());
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class GoAstParser {

    private static final boolean ANTLR_AVAILABLE = checkAntlrAvailability();
    private static final String BASE_ERROR_LISTENER_CLASS = "org.antlr.v4.runtime.BaseErrorListener";
    private static final String GO_LEXER_CLASS = "com.docarchitect.parser.GoLexer";
    private static final String GO_PARSER_CLASS = "com.docarchitect.parser.GoParser";
    private static final String SOURCE_FILE_METHOD = "sourceFile";
    private static final String STRUCT_KEYWORD = "struct";
    private static final String GO_LEXER_MISSING_MESSAGE = "GoLexer not found - ensure ANTLR grammar is generated";
    private static final String GO_PARSER_MISSING_MESSAGE = "GoParser not found - ensure ANTLR grammar is generated";
    private static final String SOURCE_FILE_INVOKE_ERROR = "Cannot invoke sourceFile on parser";

    /**
     * Regex to match struct definitions: type UserService struct { ... }
     * Captures: (1) struct name.
     */
    private static final Pattern STRUCT_PATTERN = Pattern.compile(
        "type\\s+(\\w+)\\s+struct\\s*\\{"
    );

    /**
     * Parse a Go file and extract struct definitions.
     *
     * <p>This method attempts to parse the file using ANTLR. If ANTLR parsing fails,
     * it gracefully falls back to regex-based parsing as a fallback strategy.</p>
     *
     * @param filePath path to the Go file
     * @return list of parsed structs (never null)
     * @throws IOException if the file cannot be read
     */
    public static List<GoAst.GoStruct> parseFile(Path filePath) throws IOException {
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
     * Check if ANTLR runtime and generated Go parser are available.
     */
    private static boolean checkAntlrAvailability() {
        try {
            Class.forName(BASE_ERROR_LISTENER_CLASS);
            Class.forName(GO_LEXER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Parse using ANTLR's Go grammar (most accurate).
     */
    private static List<GoAst.GoStruct> parseWithAntlr(Path filePath) throws IOException {
        String source = Files.readString(filePath);

        CharStream input = CharStreams.fromString(source);
        Lexer lexer = createGoLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = createGoParser(tokens);

        ParseTree tree = getSourceFileTree(parser);
        GoStructVisitor visitor = new GoStructVisitor();
        return visitor.visit(tree);
    }

    /**
     * Create Go Lexer via reflection to avoid hard dependency.
     */
    private static Lexer createGoLexer(CharStream input) {
        try {
            Class<?> lexerClass = Class.forName(GO_LEXER_CLASS);
            return (Lexer) lexerClass.getDeclaredConstructor(CharStream.class).newInstance(input);
        } catch (Exception e) {
            throw new RuntimeException(GO_LEXER_MISSING_MESSAGE, e);
        }
    }

    /**
     * Create Go Parser via reflection.
     */
    private static Parser createGoParser(CommonTokenStream tokens) {
        try {
            Class<?> parserClass = Class.forName(GO_PARSER_CLASS);
            return (Parser) parserClass.getDeclaredConstructor(CommonTokenStream.class).newInstance(tokens);
        } catch (Exception e) {
            throw new RuntimeException(GO_PARSER_MISSING_MESSAGE, e);
        }
    }

    /**
     * Get sourceFile rule from parser via reflection.
     */
    private static ParseTree getSourceFileTree(Parser parser) {
        try {
            return (ParseTree) parser.getClass().getMethod(SOURCE_FILE_METHOD).invoke(parser);
        } catch (Exception e) {
            throw new RuntimeException(SOURCE_FILE_INVOKE_ERROR, e);
        }
    }

    /**
     * ANTLR ParseTree visitor for extracting Go structs.
     */
    private static class GoStructVisitor {
        private final List<GoAst.GoStruct> structs = new ArrayList<>();

        List<GoAst.GoStruct> visit(ParseTree tree) {
            walkTree(tree);
            return structs;
        }

        private void walkTree(ParseTree node) {
            if (node == null) return;

            String text = node.getText();
            if (text != null && text.contains(STRUCT_KEYWORD)) {
                GoAst.GoStruct struct = extractStructFromText(text);
                if (struct != null) {
                    structs.add(struct);
                }
            }

            // Recursively visit children
            for (int i = 0; i < node.getChildCount(); i++) {
                walkTree(node.getChild(i));
            }
        }

        private GoAst.GoStruct extractStructFromText(String text) {
            Matcher matcher = STRUCT_PATTERN.matcher(text);
            if (matcher.find()) {
                String name = matcher.group(1);
                return new GoAst.GoStruct(name, List.of(), "");
            }
            return null;
        }
    }

    /**
     * Fallback regex-based parsing for when ANTLR is not available.
     */
    private static List<GoAst.GoStruct> parseWithRegex(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        List<GoAst.GoStruct> structs = new ArrayList<>();

        Matcher matcher = STRUCT_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            structs.add(new GoAst.GoStruct(name, List.of(), ""));
        }

        return structs;
    }
}
