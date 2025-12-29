package com.docarchitect.core.scanner.impl.ruby.util;

import com.docarchitect.core.scanner.ast.AstParser;
import com.docarchitect.core.scanner.ast.RubyAst;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter that converts {@link RubyAstParser} output to {@link AstParser} interface.
 *
 * <p>This lightweight adapter delegates parsing to the existing {@link RubyAstParser}
 * and converts the result to the standardized {@link RubyAst.RubyClass} format.
 *
 * <p>The adapter provides a unified interface for Ruby/Rails AST parsing, supporting
 * both controller classes and routes.rb files.
 *
 * @since 1.0.0
 */
public class RubyAstParserAdapter implements AstParser<RubyAst.RubyClass> {

    @Override
    public List<RubyAst.RubyClass> parseFile(Path filePath) throws IOException {
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(filePath);
        return classes.stream()
            .map(this::convert)
            .collect(Collectors.toList());
    }

    @Override
    public boolean isAvailable() {
        return true; // Ruby parser always available (has regex fallback)
    }

    @Override
    public String getLanguage() {
        return Technologies.RUBY;
    }

    /**
     * Convert RubyAstParser.RubyClass to RubyAst.RubyClass.
     *
     * @param old parser output
     * @return standardized AST node
     */
    private RubyAst.RubyClass convert(RubyAstParser.RubyClass old) {
        List<RubyAst.Method> methods = old.methods.stream()
            .map(m -> new RubyAst.Method(m.name, m.params, m.lineNumber))
            .collect(Collectors.toList());

        return new RubyAst.RubyClass(
            old.name,
            old.superclass,
            methods,
            old.beforeActions,
            old.lineNumber
        );
    }
}
