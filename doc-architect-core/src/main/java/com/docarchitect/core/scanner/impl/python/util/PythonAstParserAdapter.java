package com.docarchitect.core.scanner.impl.python.util;

import com.docarchitect.core.scanner.ast.AstParser;
import com.docarchitect.core.scanner.ast.PythonAst;
import  com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter that converts {@link PythonAstParser} output to {@link AstParser} interface.
 *
 * <p>This lightweight adapter delegates parsing to the existing {@link PythonAstParser}
 * and converts the result to the standardized {@link PythonAst.PythonClass} format.
 *
 * @since 1.0.0
 */
public class PythonAstParserAdapter implements AstParser<PythonAst.PythonClass> {

    @Override
    public List<PythonAst.PythonClass> parseFile(Path filePath) throws IOException {
        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(filePath);
        return classes.stream()
            .map(this::convert)
            .collect(Collectors.toList());
    }

    @Override
    public boolean isAvailable() {
        return true; // Python parser always available (has regex fallback)
    }

    @Override
    public String getLanguage() {
        return Technologies.PYTHON;
    }

    private PythonAst.PythonClass convert(PythonAstParser.PythonClass old) {
        List<PythonAst.Field> fields = old.fields.stream()
            .map(f -> new PythonAst.Field(f.name, f.type, f.value, f.decorators))
            .collect(Collectors.toList());

        return new PythonAst.PythonClass(
            old.name,
            old.baseClasses,
            fields,
            old.decorators,
            old.lineNumber
        );
    }
}
