package com.docarchitect.core.scanner.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node types for Ruby/Rails source code.
 *
 * <p>This class contains record types representing parsed Ruby syntax elements
 * (classes, methods, before_actions). These records are immutable and provide
 * a clean, type-safe API for working with Ruby AST.
 *
 * <p><b>Supported Ruby Versions:</b></p>
 * <ul>
 *   <li>Ruby 2.x (all versions)</li>
 *   <li>Ruby 3.x (all versions)</li>
 *   <li>Rails 5.x, 6.x, 7.x</li>
 * </ul>
 *
 * @see com.docarchitect.core.scanner.impl.ruby.util.RubyAstParser
 * @since 1.0.0
 */
public final class RubyAst {

    private RubyAst() {
        // Utility class - no instantiation
    }

    /**
     * Represents a Ruby class definition.
     *
     * <p>Example:
     * <pre>{@code
     * class UsersController < ApplicationController
     *   before_action :authenticate_user
     *
     *   def index
     *     # ...
     *   end
     * end
     * }</pre>
     *
     * @param name class name (e.g., "UsersController")
     * @param superclass parent class name (e.g., "ApplicationController")
     * @param methods list of method definitions
     * @param beforeActions list of before_action callback names
     * @param lineNumber line number where class is defined (1-indexed)
     */
    public record RubyClass(
        String name,
        String superclass,
        List<Method> methods,
        List<String> beforeActions,
        int lineNumber
    ) {
        public RubyClass {
            Objects.requireNonNull(name, "name must not be null");
            superclass = superclass != null ? superclass : "";
            methods = methods != null ? List.copyOf(methods) : List.of();
            beforeActions = beforeActions != null ? List.copyOf(beforeActions) : List.of();
        }

        /**
         * Check if this class inherits from a specific parent class.
         *
         * <p>Matches exact name, fully-qualified name (e.g., "Rails::Controller"),
         * or partial qualified name (e.g., "ApplicationController").
         *
         * @param parentClass parent class name to check
         * @return true if this class inherits from parentClass
         */
        public boolean inheritsFrom(String parentClass) {
            if (superclass == null || superclass.isEmpty()) {
                return false;
            }
            return superclass.equals(parentClass) ||
                   superclass.endsWith("::" + parentClass) ||
                   superclass.contains(parentClass);
        }

        /**
         * Check if this is a Rails controller class.
         *
         * @return true if class inherits from ApplicationController or ActionController
         */
        public boolean isController() {
            return inheritsFrom("ApplicationController") ||
                   inheritsFrom("ActionController::Base") ||
                   inheritsFrom("ActionController");
        }
    }

    /**
     * Represents a Ruby method definition.
     *
     * <p>Example:
     * <pre>{@code
     * def show(id)
     *   # ...
     * end
     * }</pre>
     *
     * @param name method name (e.g., "show", "index")
     * @param params list of parameter names (e.g., ["id"])
     * @param lineNumber line number where method is defined (1-indexed)
     */
    public record Method(
        String name,
        List<String> params,
        int lineNumber
    ) {
        public Method {
            Objects.requireNonNull(name, "name must not be null");
            params = params != null ? List.copyOf(params) : List.of();
        }

        /**
         * Check if this is a standard Rails RESTful action.
         *
         * @return true if method is index/show/create/update/destroy
         */
        public boolean isRestfulAction() {
            return "index".equals(name) ||
                   "show".equals(name) ||
                   "create".equals(name) ||
                   "update".equals(name) ||
                   "destroy".equals(name) ||
                   "new".equals(name) ||
                   "edit".equals(name);
        }
    }

    /**
     * Represents a Rails route definition from routes.rb.
     *
     * <p>Example:
     * <pre>{@code
     * resources :users
     * get '/profile', to: 'users#show'
     * }</pre>
     *
     * @param verb HTTP verb (GET, POST, PUT, PATCH, DELETE, or "resources")
     * @param path route path (e.g., "/profile", ":users")
     * @param controller controller name (e.g., "users")
     * @param action action name (e.g., "show")
     * @param lineNumber line number where route is defined (1-indexed)
     */
    public record Route(
        String verb,
        String path,
        String controller,
        String action,
        int lineNumber
    ) {
        public Route {
            Objects.requireNonNull(verb, "verb must not be null");
            Objects.requireNonNull(path, "path must not be null");
        }

        /**
         * Check if this is a RESTful resource route.
         *
         * @return true if verb is "resources" or "resource"
         */
        public boolean isResourceRoute() {
            return "resources".equalsIgnoreCase(verb) ||
                   "resource".equalsIgnoreCase(verb);
        }
    }
}
