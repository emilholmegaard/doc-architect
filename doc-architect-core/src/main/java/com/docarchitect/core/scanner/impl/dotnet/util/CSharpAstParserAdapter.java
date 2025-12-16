package com.docarchitect.core.scanner.impl.dotnet.util;

import com.docarchitect.core.scanner.ast.AstParser;
import com.docarchitect.core.scanner.ast.DotNetAst;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Adapter that implements AstParser interface for C# using CSharpAstParser.
 *
 * <p>This adapter bridges the gap between the scanner framework's AstParser interface
 * and the CSharpAstParser utility class.
 *
 * @since 1.0.0
 */
public class CSharpAstParserAdapter implements AstParser<DotNetAst.CSharpClass> {

    @Override
    public List<DotNetAst.CSharpClass> parseFile(Path filePath) throws IOException {
        return CSharpAstParser.parseFile(filePath);
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available with regex fallback
    }

    @Override
    public String getLanguage() {
        return "csharp";
    }
}
