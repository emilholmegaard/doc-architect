package com.docarchitect.core.scanner.ast;

import java.util.List;

/**
 * AST node types for JavaScript and TypeScript source code.
 *
 * <p>This class contains record types representing parsed JavaScript/TypeScript syntax elements
 * (functions, classes, expressions, decorators). These records are immutable and provide
 * a clean, type-safe API for working with JS/TS AST.
 *
 * <p><b>Supported Languages:</b></p>
 * <ul>
 *   <li>JavaScript (ES5, ES6, ES2015+)</li>
 *   <li>TypeScript (all versions)</li>
 *   <li>JSX/TSX</li>
 * </ul>
 *
 * <p><b>Parser Implementation:</b></p>
 * <p>Uses Acorn (JavaScript) or TypeScript compiler API via Node.js process execution.</p>
 *
 * @see com.docarchitect.core.scanner.impl.javascript.util.JavaScriptAstParser
 * @since 1.0.0
 */
public final class JavaScriptAst {

    private JavaScriptAst() {
        // Utility class - no instantiation
    }

    /**
     * Represents a JavaScript/TypeScript class or object.
     *
     * <p>Example:
     * <pre>{@code
     * class UserService {
     *     constructor(db) {
     *         this.db = db;
     *     }
     *
     *     async getUser(id) {
     *         return await this.db.findOne({ id });
     *     }
     * }
     * }</pre>
     *
     * @param name class/object name
     * @param methods list of method definitions
     * @param properties list of property definitions
     * @param decorators list of decorators (TypeScript)
     */
    public record JsClass(
        String name,
        List<JsMethod> methods,
        List<JsProperty> properties,
        List<JsDecorator> decorators
    ) {
        public JsClass {
            methods = methods != null ? List.copyOf(methods) : List.of();
            properties = properties != null ? List.copyOf(properties) : List.of();
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }
    }

    /**
     * Represents a JavaScript/TypeScript function or method.
     *
     * <p>Example (Express route):
     * <pre>{@code
     * app.get('/users/:id', async (req, res) => {
     *     const user = await User.findById(req.params.id);
     *     res.json(user);
     * });
     * }</pre>
     *
     * @param name function/method name (may be null for anonymous functions)
     * @param parameters list of parameter names
     * @param isAsync true if function is async
     * @param isArrow true if function uses arrow syntax
     * @param decorators list of decorators (TypeScript)
     */
    public record JsMethod(
        String name,
        List<String> parameters,
        boolean isAsync,
        boolean isArrow,
        List<JsDecorator> decorators
    ) {
        public JsMethod {
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            decorators = decorators != null ? List.copyOf(decorators) : List.of();
        }
    }

    /**
     * Represents a property or field.
     *
     * @param name property name
     * @param type property type (TypeScript only)
     * @param value initial value (if any)
     */
    public record JsProperty(
        String name,
        String type,
        String value
    ) {}

    /**
     * Represents a decorator (TypeScript) or route registration (Express).
     *
     * <p>Examples:
     * <pre>{@code
     * // TypeScript decorator
     * @Component({ selector: 'app-user' })
     *
     * // Express route
     * app.get('/users', handler)
     * router.post('/login', handler)
     * }</pre>
     *
     * @param name decorator/route name (e.g., "Component", "get", "post")
     * @param arguments decorator/route arguments
     */
    public record JsDecorator(
        String name,
        List<String> arguments
    ) {
        public JsDecorator {
            arguments = arguments != null ? List.copyOf(arguments) : List.of();
        }
    }

    /**
     * Represents an Express.js route registration.
     *
     * <p>Example:
     * <pre>{@code
     * app.get('/api/users/:id', authMiddleware, userController.getById);
     * router.post('/login', loginHandler);
     * }</pre>
     *
     * @param routerName router object name (e.g., "app", "router")
     * @param httpMethod HTTP method (e.g., "get", "post", "put", "delete", "patch")
     * @param path route path (e.g., "/api/users/:id")
     * @param handlerName handler function name (if available)
     */
    public record ExpressRoute(
        String routerName,
        String httpMethod,
        String path,
        String handlerName
    ) {}

    /**
     * Represents an import/require statement.
     *
     * <p>Examples:
     * <pre>{@code
     * import express from 'express';
     * import { Router } from 'express';
     * const express = require('express');
     * }</pre>
     *
     * @param moduleName module being imported (e.g., "express")
     * @param importedNames specific names imported (e.g., ["Router"])
     * @param defaultImport default import name (e.g., "express")
     * @param isRequire true if CommonJS require(), false if ES6 import
     */
    public record JsImport(
        String moduleName,
        List<String> importedNames,
        String defaultImport,
        boolean isRequire
    ) {
        public JsImport {
            importedNames = importedNames != null ? List.copyOf(importedNames) : List.of();
        }
    }
}
