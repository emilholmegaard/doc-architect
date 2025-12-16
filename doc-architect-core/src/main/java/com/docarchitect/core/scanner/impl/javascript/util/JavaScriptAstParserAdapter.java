package com.docarchitect.core.scanner.impl.javascript.util;

import com.docarchitect.core.scanner.ast.AstParser;
import com.docarchitect.core.scanner.ast.JavaScriptAst;

import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Adapter that implements AstParser interface for JavaScript using JavaScriptAstParser.
 *
 * <p>This adapter bridges the gap between the scanner framework's AstParser interface
 * and the JavaScriptAstParser utility class.
 *
 * @since 1.0.0
 */
public class JavaScriptAstParserAdapter implements AstParser<JavaScriptAst.ExpressRoute> {

    @Override
    public List<JavaScriptAst.ExpressRoute> parseFile(Path filePath) throws IOException {
        return JavaScriptAstParser.parseFile(filePath);
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available with regex fallback
    }

    @Override
    public String getLanguage() {
        return Technologies.JAVASCRIPT;
    }
}
