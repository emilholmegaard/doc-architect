package com.docarchitect.core.scanner.ast;

import java.util.List;

/**
 * AST node types for .NET (C#) source code.
 *
 * <p>This class contains record types representing parsed C# syntax elements
 * (classes, properties, methods, attributes). These records are immutable and provide
 * a clean, type-safe API for working with C# AST.
 *
 * <p><b>Parser Implementation:</b></p>
 * <p>Uses Roslyn compiler API or external process execution for parsing C# source files.</p>
 *
 * @see com.docarchitect.core.scanner.impl.dotnet.util.DotNetAstParser
 * @since 1.0.0
 */
public final class DotNetAst {

    private DotNetAst() {
        // Utility class - no instantiation
    }

    /**
     * Represents a C# class definition.
     *
     * <p>Example:
     * <pre>{@code
     * [ApiController]
     * [Route("api/[controller]")]
     * public class UserController : ControllerBase
     * {
     *     public DbSet<User> Users { get; set; }
     * }
     * }</pre>
     *
     * @param name class name (e.g., "UserController")
     * @param baseClasses list of base class names (e.g., ["ControllerBase"])
     * @param properties list of property definitions
     * @param methods list of method definitions
     * @param attributes list of attribute names (e.g., ["ApiController", "Route"])
     * @param namespace namespace (e.g., "MyApp.Controllers")
     */
    public record CSharpClass(
        String name,
        List<String> baseClasses,
        List<Property> properties,
        List<Method> methods,
        List<Attribute> attributes,
        String namespace
    ) {
        public CSharpClass {
            baseClasses = baseClasses != null ? List.copyOf(baseClasses) : List.of();
            properties = properties != null ? List.copyOf(properties) : List.of();
            methods = methods != null ? List.copyOf(methods) : List.of();
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
        }

        /**
         * Check if this class inherits from a specific base class.
         */
        public boolean inheritsFrom(String baseClass) {
            return baseClasses.stream()
                .anyMatch(base ->
                    base.equals(baseClass) ||
                    base.endsWith("." + baseClass) ||
                    base.contains(baseClass)
                );
        }
    }

    /**
     * Represents a C# property.
     *
     * <p>Example:
     * <pre>{@code
     * public DbSet<User> Users { get; set; }
     * }</pre>
     *
     * @param name property name (e.g., "Users")
     * @param type property type (e.g., "DbSet<User>")
     * @param hasGetter true if property has getter
     * @param hasSetter true if property has setter
     * @param attributes list of attributes applied to this property
     */
    public record Property(
        String name,
        String type,
        boolean hasGetter,
        boolean hasSetter,
        List<Attribute> attributes
    ) {
        public Property {
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
        }
    }

    /**
     * Represents a C# method.
     *
     * <p>Example:
     * <pre>{@code
     * [HttpGet("{id}")]
     * public IActionResult GetById(int id)
     * {
     *     return Ok();
     * }
     * }</pre>
     *
     * @param name method name (e.g., "GetById")
     * @param returnType return type (e.g., "IActionResult")
     * @param parameters list of parameter definitions
     * @param attributes list of attributes (e.g., ["HttpGet"])
     */
    public record Method(
        String name,
        String returnType,
        List<Parameter> parameters,
        List<Attribute> attributes
    ) {
        public Method {
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
        }
    }

    /**
     * Represents a method parameter.
     *
     * @param name parameter name
     * @param type parameter type
     * @param attributes list of attributes (e.g., ["FromBody"])
     */
    public record Parameter(
        String name,
        String type,
        List<Attribute> attributes
    ) {
        public Parameter {
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
        }
    }

    /**
     * Represents a C# attribute.
     *
     * <p>Example: {@code [HttpGet("{id}")]} or {@code [ApiController]}
     *
     * @param name attribute name (e.g., "HttpGet", "ApiController")
     * @param arguments attribute arguments (e.g., ["\"{id}\""] for HttpGet)
     */
    public record Attribute(
        String name,
        List<String> arguments
    ) {
        public Attribute {
            arguments = arguments != null ? List.copyOf(arguments) : List.of();
        }
    }
}
