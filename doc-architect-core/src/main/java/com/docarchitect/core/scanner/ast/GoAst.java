package com.docarchitect.core.scanner.ast;

import java.util.List;

/**
 * AST node types for Go source code.
 *
 * <p>This class contains record types representing parsed Go syntax elements
 * (structs, interfaces, functions, methods). These records are immutable and provide
 * a clean, type-safe API for working with Go AST.
 *
 * <p><b>Note:</b> For go.mod parsing, use {@link com.docarchitect.core.scanner.impl.go.GoModScanner}
 * which uses regex (appropriate for Go's simple module file format).
 *
 * <p><b>Parser Implementation:</b></p>
 * <p>Uses go/parser from Go standard library via process execution.</p>
 *
 * @since 1.0.0
 */
public final class GoAst {

    private GoAst() {
        // Utility class - no instantiation
    }

    /**
     * Represents a Go struct definition.
     *
     * <p>Example:
     * <pre>{@code
     * type User struct {
     *     ID       int    `json:"id" db:"id"`
     *     Username string `json:"username" db:"username"`
     *     Email    string `json:"email" db:"email"`
     * }
     * }</pre>
     *
     * @param name struct name (e.g., "User")
     * @param fields list of field definitions
     * @param packageName package name (e.g., "models")
     */
    public record GoStruct(
        String name,
        List<Field> fields,
        String packageName
    ) {
        public GoStruct {
            fields = fields != null ? List.copyOf(fields) : List.of();
        }
    }

    /**
     * Represents a Go struct field.
     *
     * <p>Example:
     * <pre>{@code
     * Username string `json:"username" db:"username"`
     * }</pre>
     *
     * @param name field name (e.g., "Username")
     * @param type field type (e.g., "string", "*User", "[]byte")
     * @param tag struct tag (e.g., "json:\"username\" db:\"username\"")
     */
    public record Field(
        String name,
        String type,
        String tag
    ) {}

    /**
     * Represents a Go interface definition.
     *
     * <p>Example:
     * <pre>{@code
     * type UserService interface {
     *     GetUser(id int) (*User, error)
     *     CreateUser(user *User) error
     * }
     * }</pre>
     *
     * @param name interface name
     * @param methods list of method signatures
     * @param packageName package name
     */
    public record GoInterface(
        String name,
        List<MethodSignature> methods,
        String packageName
    ) {
        public GoInterface {
            methods = methods != null ? List.copyOf(methods) : List.of();
        }
    }

    /**
     * Represents a Go function or method.
     *
     * <p>Example:
     * <pre>{@code
     * func (s *UserService) GetUser(id int) (*User, error) {
     *     return s.db.FindUser(id)
     * }
     * }</pre>
     *
     * @param name function/method name
     * @param receiver receiver type (e.g., "*UserService"), null for top-level functions
     * @param parameters list of parameters
     * @param returnTypes list of return types
     * @param packageName package name
     */
    public record GoFunction(
        String name,
        String receiver,
        List<Parameter> parameters,
        List<String> returnTypes,
        String packageName
    ) {
        public GoFunction {
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            returnTypes = returnTypes != null ? List.copyOf(returnTypes) : List.of();
        }

        /**
         * Check if this is a method (has receiver).
         */
        public boolean isMethod() {
            return receiver != null && !receiver.isEmpty();
        }
    }

    /**
     * Represents a method signature (interface method).
     *
     * @param name method name
     * @param parameters list of parameters
     * @param returnTypes list of return types
     */
    public record MethodSignature(
        String name,
        List<Parameter> parameters,
        List<String> returnTypes
    ) {
        public MethodSignature {
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            returnTypes = returnTypes != null ? List.copyOf(returnTypes) : List.of();
        }
    }

    /**
     * Represents a function/method parameter.
     *
     * @param name parameter name (may be empty for unnamed parameters)
     * @param type parameter type
     */
    public record Parameter(
        String name,
        String type
    ) {}

    /**
     * Represents a Go import statement.
     *
     * <p>Example:
     * <pre>{@code
     * import (
     *     "fmt"
     *     "database/sql"
     *     _ "github.com/lib/pq"
     *     json "encoding/json"
     * )
     * }</pre>
     *
     * @param path import path (e.g., "github.com/lib/pq")
     * @param alias import alias (e.g., "json"), null if no alias
     * @param isBlank true if blank import (underscore)
     */
    public record GoImport(
        String path,
        String alias,
        boolean isBlank
    ) {}
}
