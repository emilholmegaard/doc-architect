package com.docarchitect.core.scanner.ast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AstParserFactory}.
 */
class AstParserFactoryTest {

    @AfterEach
    void cleanUp() {
        AstParserFactory.clearCache();
    }

    @Test
    void getPythonParser_returnsValidParser() {
        // When
        AstParser<PythonAst.PythonClass> parser = AstParserFactory.getPythonParser();

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser.isAvailable()).isTrue();
        assertThat(parser.getLanguage()).isEqualTo("python");
    }

    @Test
    void getPythonParser_cachesSameInstance() {
        // When
        AstParser<PythonAst.PythonClass> parser1 = AstParserFactory.getPythonParser();
        AstParser<PythonAst.PythonClass> parser2 = AstParserFactory.getPythonParser();

        // Then: Should return cached instance
        assertThat(parser1).isSameAs(parser2);
    }

    @Test
    void getDotNetParser_returnsValidParser() {
        // When
        AstParser<DotNetAst.CSharpClass> parser = AstParserFactory.getDotNetParser();

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser.isAvailable()).isTrue();
        assertThat(parser.getLanguage()).isEqualTo("csharp");
    }

    @Test
    void getJavaScriptParser_returnsValidParser() {
        // When
        AstParser<JavaScriptAst.ExpressRoute> parser = AstParserFactory.getJavaScriptParser();

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser.isAvailable()).isTrue();
        assertThat(parser.getLanguage()).isEqualTo("javascript");
    }

    @Test
    void getGoParser_returnsValidParser() {
        // When
        AstParser<GoAst.GoStruct> parser = AstParserFactory.getGoParser();

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser.isAvailable()).isTrue();
        assertThat(parser.getLanguage()).isEqualTo("go");
    }

    @Test
    void isAvailable_pythonReturnsTrue() {
        // When/Then
        assertThat(AstParserFactory.isAvailable("python")).isTrue();
    }

    @Test
    void isAvailable_dotnetReturnsTrue() {
        // When/Then: Implemented with Roslyn + regex fallback
        assertThat(AstParserFactory.isAvailable("dotnet")).isTrue();
        assertThat(AstParserFactory.isAvailable("csharp")).isTrue();
    }

    @Test
    void isAvailable_javascriptReturnsTrue() {
        // When/Then: Implemented with Acorn/TS compiler + regex fallback
        assertThat(AstParserFactory.isAvailable("javascript")).isTrue();
        assertThat(AstParserFactory.isAvailable("typescript")).isTrue();
    }

    @Test
    void isAvailable_goReturnsTrue() {
        // When/Then: Implemented with go/parser + regex fallback
        assertThat(AstParserFactory.isAvailable("go")).isTrue();
        assertThat(AstParserFactory.isAvailable("golang")).isTrue();
    }

    @Test
    void isAvailable_unknownLanguageReturnsFalse() {
        // When/Then
        assertThat(AstParserFactory.isAvailable("ruby")).isFalse();
        assertThat(AstParserFactory.isAvailable("php")).isFalse();
    }

    @Test
    void clearCache_removesAllCachedParsers() {
        // Given
        AstParser<PythonAst.PythonClass> parser1 = AstParserFactory.getPythonParser();

        // When
        AstParserFactory.clearCache();
        AstParser<PythonAst.PythonClass> parser2 = AstParserFactory.getPythonParser();

        // Then: Should be different instances (cache was cleared)
        assertThat(parser1).isNotSameAs(parser2);
    }
}
