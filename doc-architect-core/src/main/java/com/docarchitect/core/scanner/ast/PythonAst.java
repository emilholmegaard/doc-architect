package com.docarchitect.core.scanner.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node types for Python source code.
 *
 * <p>This class contains record types representing parsed Python syntax elements
 * (classes, fields, methods, decorators). These records are immutable and provide
 * a clean, type-safe API for working with Python AST.
 *
 * <p><b>Supported Python Versions:</b></p>
 * <ul>
 *   <li>Python 2.7</li>
 *   <li>Python 3.x (all versions)</li>
 * </ul>
 *
 * @see com.docarchitect.core.scanner.impl.python.util.PythonAstParser
 * @since 1.0.0
 */
public final class PythonAst {

    private PythonAst() {
        // Utility class - no instantiation
    }

    /**
     * Represents a Python class definition.
     *
     * <p>Example:
     * <pre>{@code
     * @dataclass
     * class User(models.Model):
     *     id: int = Column(Integer, primary_key=True)
     *     username: str = Column(String(50))
     * }</pre>
     *
     * @param name class name (e.g., "User")
     * @param baseClasses list of base class names (e.g., ["models.Model"])
     * @param fields list of field definitions
     * @param decorators list of decorator names (e.g., ["dataclass"])
     * @param lineNumber line number where class is defined (1-indexed)
     */
    public record PythonClass(
        String name,
        List<String> baseClasses,
        List<Field> fields,
        List<String> decorators,
        int lineNumber
    ) {
        public PythonClass {
            Objects.requireNonNull(name, "name must not be null");
            baseClasses = baseClasses != null ? List.copyOf(baseClasses) : List.of();
            fields = fields != null ? List.copyOf(fields) : List.of();
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }

        /**
         * Check if this class inherits from a specific parent class.
         *
         * <p>Matches exact name, fully-qualified name (e.g., "models.Model"),
         * or partial qualified name (e.g., "Model").
         *
         * @param parentClass parent class name to check
         * @return true if this class inherits from parentClass
         */
        public boolean inheritsFrom(String parentClass) {
            return baseClasses.stream()
                .anyMatch(base ->
                    base.equals(parentClass) ||
                    base.endsWith("." + parentClass) ||
                    base.contains(parentClass)
                );
        }
    }

    /**
     * Represents a Python field/attribute.
     *
     * <p>Example:
     * <pre>{@code
     * username: str = Column(String(50), nullable=False)
     * }</pre>
     *
     * @param name field name (e.g., "username")
     * @param type field type annotation (e.g., "str", "Mapped[str]")
     * @param value field value/initializer (e.g., "Column(String(50), nullable=False)")
     * @param decorators list of decorators applied to this field
     */
    public record Field(
        String name,
        String type,
        String value,
        List<String> decorators
    ) {
        public Field {
            Objects.requireNonNull(name, "name must not be null");
            type = type != null ? type : "Any";
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }
    }

    /**
     * Represents a Python method/function definition.
     *
     * @param name method name
     * @param parameters list of parameter names
     * @param returnType return type annotation (may be null)
     * @param decorators list of decorators (e.g., ["property", "staticmethod"])
     */
    public record Method(
        String name,
        List<String> parameters,
        String returnType,
        List<String> decorators
    ) {
        public Method {
            Objects.requireNonNull(name, "name must not be null");
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }
    }

    /**
     * Represents a Python import statement.
     *
     * @param moduleName imported module name (e.g., "sqlalchemy")
     * @param importedNames specific names imported (e.g., ["Column", "Integer"])
     * @param alias optional alias (e.g., "sa" in "import sqlalchemy as sa")
     */
    public record Import(
        String moduleName,
        List<String> importedNames,
        String alias
    ) {
        public Import {
            Objects.requireNonNull(moduleName, "moduleName must not be null");
            importedNames = importedNames != null ? List.copyOf(importedNames) : List.of();
        }
    }

    /**
     * Represents a Python module-level function definition.
     *
     * <p>Example:
     * <pre>{@code
     * @shared_task(queue='emails')
     * def send_email(to, subject):
     *     pass
     * }</pre>
     *
     * @param name function name (e.g., "send_email")
     * @param parameters list of parameter names
     * @param decorators list of decorator expressions (e.g., ["shared_task(queue='emails')"])
     * @param lineNumber line number where function is defined (1-indexed)
     * @param isAsync whether this is an async function
     */
    public record Function(
        String name,
        List<String> parameters,
        List<String> decorators,
        int lineNumber,
        boolean isAsync
    ) {
        public Function {
            Objects.requireNonNull(name, "name must not be null");
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }

        /**
         * Check if this function has a specific decorator.
         *
         * <p>Matches exact name or decorator with parameters.
         *
         * @param decoratorName decorator name to check (e.g., "task", "shared_task")
         * @return true if this function has the decorator
         */
        public boolean hasDecorator(String decoratorName) {
            return decorators.stream()
                .anyMatch(dec ->
                    dec.equals(decoratorName) ||
                    dec.startsWith(decoratorName + "(") ||
                    dec.contains("." + decoratorName + "(") ||
                    dec.contains("." + decoratorName)
                );
        }
    }

    /**
     * Represents a function call/invocation in Python code.
     *
     * <p>Example:
     * <pre>{@code
     * send_email.delay('user@example.com', 'Welcome')
     * process_data.apply_async(args=[123], queue='priority')
     * }</pre>
     *
     * @param functionName name of the function being called (e.g., "send_email")
     * @param method method being invoked (e.g., "delay", "apply_async")
     * @param arguments full argument string
     * @param lineNumber line number where call occurs (1-indexed)
     */
    public record FunctionCall(
        String functionName,
        String method,
        String arguments,
        int lineNumber
    ) {
        public FunctionCall {
            Objects.requireNonNull(functionName, "functionName must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }
}
