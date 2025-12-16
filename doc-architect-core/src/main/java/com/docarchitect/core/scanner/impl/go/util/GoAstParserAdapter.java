package com.docarchitect.core.scanner.impl.go.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.docarchitect.core.scanner.ast.AstParser;
import com.docarchitect.core.scanner.ast.GoAst;
import com.docarchitect.core.util.Technologies;

/**
 * Adapter that implements AstParser interface for Go using GoAstParser.
 *
 * <p>This adapter bridges the gap between the scanner framework's AstParser interface
 * and the GoAstParser utility class.
 *
 * @since 1.0.0
 */
public class GoAstParserAdapter implements AstParser<GoAst.GoStruct> {

    @Override
    public List<GoAst.GoStruct> parseFile(Path filePath) throws IOException {
        return GoAstParser.parseFile(filePath);
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available with regex fallback
    }

    @Override
    public String getLanguage() {
        return Technologies.GO;
    }
}
