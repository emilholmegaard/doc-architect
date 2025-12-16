package com.docarchitect.core.scanner.ast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PythonAst} record types.
 */
class PythonAstTest {

    @Test
    void pythonClass_inheritsFrom_exactMatch() {
        // Given
        PythonAst.PythonClass cls = new PythonAst.PythonClass(
            "User",
            List.of("Base", "Mixin"),
            List.of(),
            List.of(),
            10
        );

        // When/Then
        assertThat(cls.inheritsFrom("Base")).isTrue();
        assertThat(cls.inheritsFrom("Mixin")).isTrue();
        assertThat(cls.inheritsFrom("Other")).isFalse();
    }

    @Test
    void pythonClass_inheritsFrom_qualifiedNameMatch() {
        // Given
        PythonAst.PythonClass cls = new PythonAst.PythonClass(
            "User",
            List.of("models.Model", "mixins.TimestampMixin"),
            List.of(),
            List.of(),
            10
        );

        // When/Then
        assertThat(cls.inheritsFrom("Model")).isTrue();
        assertThat(cls.inheritsFrom("models.Model")).isTrue();
        assertThat(cls.inheritsFrom("TimestampMixin")).isTrue();
    }

    @Test
    void pythonClass_withNullFields_usesDefaults() {
        // When
        PythonAst.PythonClass cls = new PythonAst.PythonClass(
            "User",
            null, // null baseClasses
            null, // null fields
            null, // null decorators
            5
        );

        // Then
        assertThat(cls.baseClasses()).isEmpty();
        assertThat(cls.fields()).isEmpty();
        assertThat(cls.decorators()).isEmpty();
    }

    @Test
    void field_withNullType_usesAny() {
        // When
        PythonAst.Field field = new PythonAst.Field(
            "username",
            null, // null type
            "Column(String)",
            List.of()
        );

        // Then
        assertThat(field.type()).isEqualTo("Any");
    }

    @Test
    void field_immutability() {
        // Given
        List<String> decorators = new java.util.ArrayList<>();
        decorators.add("property");

        // When
        PythonAst.Field field = new PythonAst.Field("name", "str", "value", decorators);
        decorators.add("setter"); // Try to modify original list

        // Then: Field's decorators should be unchanged
        assertThat(field.decorators()).containsExactly("property");
    }

    @Test
    void method_immutability() {
        // Given
        List<String> params = new java.util.ArrayList<>();
        params.add("self");

        // When
        PythonAst.Method method = new PythonAst.Method("get_user", params, "User", List.of());
        params.add("id"); // Try to modify original list

        // Then: Method's parameters should be unchanged
        assertThat(method.parameters()).containsExactly("self");
    }

    @Test
    void import_record_creation() {
        // When
        PythonAst.Import imp = new PythonAst.Import(
            "sqlalchemy",
            List.of("Column", "Integer"),
            "sa"
        );

        // Then
        assertThat(imp.moduleName()).isEqualTo("sqlalchemy");
        assertThat(imp.importedNames()).containsExactly("Column", "Integer");
        assertThat(imp.alias()).isEqualTo("sa");
    }
}
