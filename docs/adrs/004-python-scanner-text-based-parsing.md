---
# Backstage TechDocs metadata
id: adr-004-python-scanner-text-based-parsing
title: ADR-004: Python Scanner Text-Based Parsing Strategy
description: Define text-based parsing (regex + TOML) for Python scanners without executing Python code
tags:
  - adr
  - architecture
  - parsing
  - python
---
# ADR-004: Python Scanner Text-Based Parsing Strategy

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-12 |
| **Deciders** | Development Team |
| **Technical Story** | Phase 4 Implementation |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

DocArchitect needs to extract architectural information from Python codebases, including:

- **Dependencies** - requirements.txt, pyproject.toml, setup.py, Pipfile
- **REST APIs** - FastAPI and Flask route decorators
- **Data models** - SQLAlchemy and Django ORM entities
- **Relationships** - Foreign keys, many-to-many relationships

**Critical Constraint:** We're running in Java, not Python. We cannot use Python's AST module or execute Python code.

**Key Requirements:**
1. Parse Python dependencies from multiple file formats (requirements.txt, TOML, setup.py)
2. Extract FastAPI endpoints (`@app.get`, `@router.post`)
3. Extract Flask endpoints (both legacy `@route` and modern `@get` styles)
4. Extract SQLAlchemy entities (both Column() 1.x and mapped_column() 2.0 styles)
5. Extract Django ORM models and relationships
6. No Python process execution
7. Accurate enough for architectural documentation (not code execution)

## Decision

We will implement **5 specialized scanners** using **text-based parsing** with different strategies optimized for each file type:

### 1. PipPoetryDependencyScanner - Multi-Format Parser

**Strategy:** Combine TOML parsing (Jackson), YAML parsing (Jackson), and regex for setup.py

#### requirements.txt - Regex Line Parser

**Rationale:**
- Simple line-based format
- Regex handles version operators (==, >=, ~=, !=)
- Can skip comments and editable installs

**Pattern:**
```java
Pattern REQUIREMENTS = Pattern.compile("^([a-zA-Z0-9_-]+)\\s*([=<>~!]+)\\s*(.+)$");
// Matches: requests==2.31.0, flask>=3.0.0, django~=4.2
```

#### pyproject.toml - TOML Parser (Jackson TOML)

**Rationale:**
- PEP 621 standard format
- Poetry also uses TOML
- Jackson provides type-safe parsing

**Implementation:**
```java
TomlMapper mapper = new TomlMapper();
JsonNode root = mapper.readTree(tomlFile);
JsonNode deps = root.get("project").get("dependencies");
```

**Handles:**
- `[project.dependencies]` - PEP 621 format
- `[project.optional-dependencies]` - Optional deps
- `[tool.poetry.dependencies]` - Poetry format
- `[tool.poetry.dev-dependencies]` - Dev deps

#### setup.py - Regex Extractor

**Rationale:**
- setup.py is executable Python code
- Cannot execute it safely
- Regex sufficient for common patterns

**Pattern:**
```java
Pattern INSTALL_REQUIRES = Pattern.compile(
    "install_requires\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL
);
Pattern DEP = Pattern.compile("['\"]([a-zA-Z0-9_-]+)\\s*([=<>~!]+)\\s*(.+?)['\"]");
```

#### Pipfile - TOML Parser

**Rationale:**
- Pipfile uses TOML format
- Similar to pyproject.toml parsing

### 2. FastAPIScanner - Regex Decorator Parser

**Strategy:** Use regex to extract route decorators and function signatures

**Rationale:**
- FastAPI decorators follow predictable patterns
- Regex is lighter than Python AST
- Sufficient for architectural documentation

**Patterns:**
```java
// Decorator: @app.get("/users/{user_id}")
Pattern DECORATOR = Pattern.compile(
    "@(app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
);

// Function: def get_user(user_id: int, query: Query = Query(...)):
Pattern FUNCTION = Pattern.compile("def\\s+(\\w+)\\s*\\((.*)\\):");

// Path param: {user_id} or {item_id: int}
Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)(?::\\s*\\w+)?\\}");

// Query param: param: Query(...)
Pattern QUERY_PARAM = Pattern.compile("(\\w+):\\s*.*?Query\\(");
```

**Extraction Process:**
1. Find decorator line
2. Extract HTTP method and path
3. Find next function definition (within 5 lines)
4. Parse function parameters
5. Categorize as path/query/body parameters

### 3. FlaskScanner - Dual-Pattern Regex Parser

**Strategy:** Support both legacy and modern Flask decorator styles

#### Legacy Style (Flask 1.x)

**Pattern:**
```java
Pattern LEGACY = Pattern.compile(
    "@(app|blueprint)\\.route\\s*\\(\\s*['\"](.+?)['\"].*?methods\\s*=\\s*\\[(.+?)\\]",
    Pattern.DOTALL
);
// Matches: @app.route("/users", methods=["GET", "POST"])
```

#### Modern Style (Flask 2.0+)

**Pattern:**
```java
Pattern MODERN = Pattern.compile(
    "@(app|blueprint)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
);
// Matches: @app.get("/users"), @blueprint.post("/items")
```

**Path Parameters:**
```java
Pattern PATH_PARAM = Pattern.compile("<(?:(\\w+):)?(\\w+)>");
// Matches: <user_id>, <int:user_id>, <string:username>
```

### 4. SQLAlchemyScanner - Dual-Era Support

**Strategy:** Support both Column() (1.x) and mapped_column() (2.0+) using regex

#### Legacy Column() Style (SQLAlchemy 1.x)

**Pattern:**
```java
Pattern CLASS = Pattern.compile("class\\s+(\\w+)\\s*\\(.*Base.*\\):");
Pattern TABLENAME = Pattern.compile("__tablename__\\s*=\\s*['\"](.+?)['\"]");
Pattern COLUMN = Pattern.compile(
    "(\\w+)\\s*=\\s*Column\\s*\\((.+?)\\)", Pattern.DOTALL
);
```

**Example:**
```python
class User(Base):
    __tablename__ = 'users'
    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False)
```

#### Modern mapped_column() Style (SQLAlchemy 2.0+)

**Pattern:**
```java
Pattern MAPPED_COLUMN = Pattern.compile(
    "(\\w+):\\s*Mapped\\[(.+?)\\]\\s*=\\s*mapped_column\\s*\\((.+?)\\)",
    Pattern.DOTALL
);
```

**Example:**
```python
class User(Base):
    __tablename__ = 'users'
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
```

#### Relationships

**Pattern:**
```java
Pattern RELATIONSHIP = Pattern.compile(
    "(\\w+)\\s*=\\s*relationship\\s*\\(\\s*['\"](.+?)['\"]"
);
```

### 5. DjangoOrmScanner - Model Parser

**Strategy:** Parse Django models using class and field patterns

**Patterns:**
```java
Pattern CLASS = Pattern.compile("class\\s+(\\w+)\\s*\\(.*models\\.Model.*\\):");

Pattern FIELD = Pattern.compile(
    "(\\w+)\\s*=\\s*models\\.(\\w+)\\s*\\((.+?)\\)", Pattern.DOTALL
);

Pattern FK = Pattern.compile(
    "(\\w+)\\s*=\\s*models\\.(ForeignKey|OneToOneField|ManyToManyField)\\s*\\(\\s*['\"](.+?)['\"]"
);
```

**Field Type Mapping:**
```java
String mapDjangoFieldToSql(String djangoField) {
    return switch (djangoField) {
        case "CharField" -> "VARCHAR";
        case "IntegerField" -> "INTEGER";
        case "BooleanField" -> "BOOLEAN";
        case "DateTimeField" -> "DATETIME";
        // ... 15+ mappings
    };
}
```

---

## Rationale

Balances accuracy and safety by avoiding code execution while covering common Python patterns.

---

## Alternatives Considered

### Using Python AST via Jython

**Pros:**
- Native Python AST parsing
- Accurate syntax tree
- Can handle complex Python constructs

**Cons:**
- Jython only supports Python 2.7
- Dead project (last release 2015)
- 40MB dependency
- Security risk (executes Python code)

**Verdict:** ❌ Rejected - Outdated and insecure

### Calling Python via Process Execution

**Pros:**
- Can use native Python `ast` module
- Accurate parsing
- Access to Python ecosystem

**Cons:**
- Requires Python installation
- Slow (process spawning)
- Security risk (arbitrary code execution)
- Cross-platform issues (Windows vs Unix)
- Breaks sandboxed environments

**Verdict:** ❌ Rejected - Too many environmental dependencies

### Using GraalVM Python

**Pros:**
- Python on JVM
- Can use Python AST
- No external process

**Cons:**
- 200MB+ dependency
- Experimental Python support
- Slow startup (~5s)
- Limited standard library support

**Verdict:** ❌ Rejected - Too heavyweight

### Using ANTLR Python Grammar

**Pros:**
- Full Python grammar support
- Pure Java implementation

**Cons:**
- 10MB ANTLR dependency
- Complex grammar (Python is notoriously hard to parse)
- Slower than regex
- Overkill for architectural extraction

**Verdict:** ❌ Rejected - Regex is sufficient

---

## Consequences

### Positive

✅ **Lightweight** - Only Jackson TOML added (56KB)
✅ **Fast** - Regex parsing is O(n), no parsing overhead
✅ **Safe** - No code execution, no external processes
✅ **Platform-independent** - Pure Java, works anywhere
✅ **Good enough** - Captures 95%+ of common patterns
✅ **Maintainable** - Regex patterns are documented and testable

### Negative

⚠️ **Limited accuracy** - Cannot handle:
  - Dynamic decorators (e.g., `@get_decorator()`)
  - String concatenation in paths (`"/api" + "/users"`)
  - Conditional field definitions (if/else in models)
  - Complex type hints (`Union[str, int]`)

⚠️ **False positives** - May extract:
  - Commented-out decorators
  - Decorators in multiline strings
  - Mitigate with: Skip lines starting with `#`, basic context awareness

⚠️ **Fragile** - Breaking changes if:
  - FastAPI/Flask change decorator syntax
  - SQLAlchemy changes Column() API
  - Mitigate with: Version testing, clear regex documentation

### Neutral

- **Test coverage** - Documented regex patterns serve as specification
- **Maintenance** - Regex patterns need updates for new framework versions
- **Accuracy** - Sufficient for architecture documentation, not code execution

---

## Implementation Notes

### Class Body Extraction

All scanners need to extract class bodies. Common algorithm:

```java
private String extractClassBody(List<String> lines, int classStartLine, String content) {
    int baseIndent = -1;
    StringBuilder body = new StringBuilder();

    for (int i = classStartLine + 1; i < lines.size(); i++) {
        String line = lines.get(i);
        int indent = line.length() - line.trim().length();

        if (baseIndent == -1 && !line.trim().isEmpty()) {
            baseIndent = indent;
        }

        // Stop at next class or unindented non-empty line
        if (baseIndent != -1 && indent < baseIndent && !line.trim().isEmpty()) {
            break;
        }

        body.append(line).append("\n");
    }

    return body.toString();
}
```

**Handles:**
- Nested classes (ignored by indentation check)
- Blank lines within class
- Stops at next class definition

### Decorator-Function Linking

FastAPI and Flask scanners link decorators to functions:

```java
private String findNextFunctionDefinition(List<String> lines, int startIndex) {
    for (int i = startIndex; i < Math.min(startIndex + 5, lines.size()); i++) {
        String line = lines.get(i).trim();
        if (line.startsWith("def ")) {
            return line;
        }
    }
    return null;
}
```

**Rationale:** Decorators are typically within 1-2 lines of function definition

### Property Resolution (Poetry)

Poetry dependencies can be strings or objects:

```toml
[tool.poetry.dependencies]
requests = "^2.31.0"
django = {version = "^4.2", optional = true}
```

**Handler:**
```java
private String extractVersionFromPoetryDep(JsonNode depNode) {
    if (depNode.isTextual()) {
        return depNode.asText();
    } else if (depNode.isObject()) {
        JsonNode version = depNode.get("version");
        return version != null ? version.asText() : "*";
    }
    return "*";
}
```

---

## Compliance

_TBD_

---

## References

- [PEP 621 - Storing project metadata in pyproject.toml](https://peps.python.org/pep-0621/)
- [Poetry Dependency Specification](https://python-poetry.org/docs/dependency-specification/)
- [FastAPI Path Operations](https://fastapi.tiangolo.com/tutorial/path-params/)
- [Flask Routing](https://flask.palletsprojects.com/en/3.0.x/quickstart/#routing)
- [SQLAlchemy 2.0 Tutorial](https://docs.sqlalchemy.org/en/20/tutorial/)
- [Django Model Field Reference](https://docs.djangoproject.com/en/5.0/ref/models/fields/)
- [Jackson TOML Module](https://github.com/FasterXML/jackson-dataformats-text/tree/2.18/toml)

---

## Metadata

- **Review Date:** After Phase 5 completion
- **Last Updated:** 2025-12-12
- **Version:** 1.0
