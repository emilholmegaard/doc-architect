package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AbstractJavaParserScanner utility methods.
 *
 * <p>These tests verify the JavaParser AST utilities used by 3 Java scanners:
 * Spring REST API Scanner, JPA Entity Scanner, and Kafka Scanner.
 */
class AbstractJavaParserScannerTest {

    @TempDir
    Path tempDir;

    private TestJavaParserScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new TestJavaParserScanner();
    }

    // ==================== Java File Parsing Tests ====================

    @Test
    void parseJavaFile_withValidJavaFile_returnsCompilationUnit() throws IOException {
        Path javaFile = tempDir.resolve("User.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
                private String name;
            }
            """);

        Optional<CompilationUnit> cu = scanner.parseJavaFile(javaFile);

        assertThat(cu).isPresent();
        assertThat(scanner.getPackageName(cu.get())).isEqualTo("com.example");
    }

    @Test
    void parseJavaFile_withInvalidJavaFile_returnsEmpty() throws IOException {
        Path javaFile = tempDir.resolve("Invalid.java");
        Files.writeString(javaFile, "this is not valid Java code {{{");

        Optional<CompilationUnit> cu = scanner.parseJavaFile(javaFile);

        assertThat(cu).isEmpty();
    }

    @Test
    void parseJavaFile_withAnnotations_parsesSuccessfully() throws IOException {
        Path javaFile = tempDir.resolve("Controller.java");
        Files.writeString(javaFile, """
            package com.example.api;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                @GetMapping("/{id}")
                public User getUser(@PathVariable Long id) {
                    return null;
                }
            }
            """);

        Optional<CompilationUnit> cu = scanner.parseJavaFile(javaFile);

        assertThat(cu).isPresent();
        List<ClassOrInterfaceDeclaration> classes = scanner.findClasses(cu.get());
        assertThat(classes).hasSize(1);
        assertThat(scanner.hasAnnotation(classes.get(0), "RestController")).isTrue();
    }

    // ==================== Class and Type Utilities Tests ====================

    @Test
    void findClasses_withMultipleClasses_returnsAll() throws IOException {
        Path javaFile = tempDir.resolve("Multiple.java");
        Files.writeString(javaFile, """
            package com.example;

            class User {
            }

            class Order {
            }

            interface Repository {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        List<ClassOrInterfaceDeclaration> classes = scanner.findClasses(cu);

        assertThat(classes).hasSize(3);
        assertThat(classes).extracting(ClassOrInterfaceDeclaration::getNameAsString)
            .containsExactly("User", "Order", "Repository");
    }

    @Test
    void hasAnnotation_withAnnotationPresent_returnsTrue() throws IOException {
        Path javaFile = tempDir.resolve("Entity.java");
        Files.writeString(javaFile, """
            package com.example;

            @Entity
            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        assertThat(scanner.hasAnnotation(clazz, "Entity")).isTrue();
    }

    @Test
    void hasAnnotation_withAnnotationAbsent_returnsFalse() throws IOException {
        Path javaFile = tempDir.resolve("Plain.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        assertThat(scanner.hasAnnotation(clazz, "Entity")).isFalse();
    }

    @Test
    void getAnnotationAttribute_withValueAttribute_returnsValue() throws IOException {
        Path javaFile = tempDir.resolve("TableAnnotation.java");
        Files.writeString(javaFile, """
            package com.example;

            @Table(name = "users")
            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        String tableName = scanner.getAnnotationAttribute(clazz, "Table", "name");

        assertThat(tableName).isEqualTo("users");
    }

    @Test
    void getAnnotationAttribute_withoutAttribute_returnsNull() throws IOException {
        Path javaFile = tempDir.resolve("SimpleAnnotation.java");
        Files.writeString(javaFile, """
            package com.example;

            @Entity
            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        String tableName = scanner.getAnnotationAttribute(clazz, "Entity", "name");

        assertThat(tableName).isNull();
    }

    @Test
    void getPackageName_withPackage_returnsPackageName() throws IOException {
        Path javaFile = tempDir.resolve("WithPackage.java");
        Files.writeString(javaFile, """
            package com.example.domain;

            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();

        String packageName = scanner.getPackageName(cu);

        assertThat(packageName).isEqualTo("com.example.domain");
    }

    @Test
    void getPackageName_withoutPackage_returnsEmpty() throws IOException {
        Path javaFile = tempDir.resolve("NoPackage.java");
        Files.writeString(javaFile, """
            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();

        String packageName = scanner.getPackageName(cu);

        assertThat(packageName).isEmpty();
    }

    @Test
    void getFullyQualifiedName_withPackage_returnsFullName() throws IOException {
        Path javaFile = tempDir.resolve("User.java");
        Files.writeString(javaFile, """
            package com.example.domain;

            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        String fqn = scanner.getFullyQualifiedName(cu, clazz);

        assertThat(fqn).isEqualTo("com.example.domain.User");
    }

    @Test
    void getFullyQualifiedName_withoutPackage_returnsClassName() throws IOException {
        Path javaFile = tempDir.resolve("User.java");
        Files.writeString(javaFile, """
            public class User {
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        String fqn = scanner.getFullyQualifiedName(cu, clazz);

        assertThat(fqn).isEqualTo("User");
    }

    // ==================== Method Utilities Tests ====================

    @Test
    void findMethodsWithAnnotation_withMultipleAnnotatedMethods_returnsMatches() throws IOException {
        Path javaFile = tempDir.resolve("Controller.java");
        Files.writeString(javaFile, """
            package com.example;

            public class UserController {
                @GetMapping("/users")
                public List<User> getAll() { return null; }

                @GetMapping("/{id}")
                public User getOne(Long id) { return null; }

                @PostMapping("/users")
                public User create(User user) { return null; }
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        List<MethodDeclaration> getMethods = scanner.findMethodsWithAnnotation(clazz, "GetMapping");

        assertThat(getMethods).hasSize(2);
        assertThat(getMethods).extracting(MethodDeclaration::getNameAsString)
            .containsExactly("getAll", "getOne");
    }

    @Test
    void findMethodsWithAnnotation_withNoMatches_returnsEmpty() throws IOException {
        Path javaFile = tempDir.resolve("Plain.java");
        Files.writeString(javaFile, """
            package com.example;

            public class Service {
                public void doSomething() { }
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        List<MethodDeclaration> methods = scanner.findMethodsWithAnnotation(clazz, "GetMapping");

        assertThat(methods).isEmpty();
    }

    @Test
    void hasAnnotation_onMethod_withAnnotationPresent_returnsTrue() throws IOException {
        Path javaFile = tempDir.resolve("Method.java");
        Files.writeString(javaFile, """
            package com.example;

            public class UserController {
                @GetMapping("/users")
                public List<User> getAll() { return null; }
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);
        MethodDeclaration method = clazz.getMethods().get(0);

        assertThat(scanner.hasAnnotation(method, "GetMapping")).isTrue();
    }

    @Test
    void getAnnotationAttribute_onMethod_returnsValue() throws IOException {
        Path javaFile = tempDir.resolve("Mapping.java");
        Files.writeString(javaFile, """
            package com.example;

            public class UserController {
                @GetMapping(path = "/users")
                public List<User> getAll() { return null; }
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);
        MethodDeclaration method = clazz.getMethods().get(0);

        String path = scanner.getAnnotationAttribute(method, "GetMapping", "path");

        assertThat(path).isEqualTo("/users");
    }

    // ==================== Field Utilities Tests ====================

    @Test
    void findFields_withMultipleFields_returnsAll() throws IOException {
        Path javaFile = tempDir.resolve("Entity.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
                private Long id;
                private String name;
                private String email;
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);

        List<FieldDeclaration> fields = scanner.findFields(clazz);

        assertThat(fields).hasSize(3);
    }

    @Test
    void hasAnnotation_onField_withAnnotationPresent_returnsTrue() throws IOException {
        Path javaFile = tempDir.resolve("Field.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
                @Id
                private Long id;
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);
        FieldDeclaration field = clazz.getFields().get(0);

        assertThat(scanner.hasAnnotation(field, "Id")).isTrue();
    }

    @Test
    void getAnnotationAttribute_onField_returnsValue() throws IOException {
        Path javaFile = tempDir.resolve("Column.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
                @Column(name = "user_name")
                private String name;
            }
            """);

        CompilationUnit cu = scanner.parseJavaFile(javaFile).get();
        ClassOrInterfaceDeclaration clazz = scanner.findClasses(cu).get(0);
        FieldDeclaration field = clazz.getFields().get(0);

        String columnName = scanner.getAnnotationAttribute(field, "Column", "name");

        assertThat(columnName).isEqualTo("user_name");
    }

    // ==================== String Utility Tests ====================

    @Test
    void cleanStringLiteral_withDoubleQuotes_removesQuotes() {
        String result = scanner.cleanStringLiteral("\"hello\"");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void cleanStringLiteral_withSingleQuotes_removesQuotes() {
        String result = scanner.cleanStringLiteral("'hello'");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void cleanStringLiteral_withoutQuotes_returnsOriginal() {
        String result = scanner.cleanStringLiteral("hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void cleanStringLiteral_withNull_returnsEmpty() {
        String result = scanner.cleanStringLiteral(null);

        assertThat(result).isEmpty();
    }

    @Test
    void cleanStringLiteral_withWhitespace_trimsAndRemovesQuotes() {
        String result = scanner.cleanStringLiteral("  \"hello\"  ");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void toSnakeCase_withCamelCase_convertsToSnakeCase() {
        assertThat(scanner.toSnakeCase("UserAccount")).isEqualTo("user_account");
        assertThat(scanner.toSnakeCase("OrderItem")).isEqualTo("order_item");
        assertThat(scanner.toSnakeCase("APIEndpoint")).isEqualTo("api_endpoint");
    }

    @Test
    void toSnakeCase_withSingleWord_returnsLowercase() {
        assertThat(scanner.toSnakeCase("User")).isEqualTo("user");
        assertThat(scanner.toSnakeCase("ORDER")).isEqualTo("order");
    }

    @Test
    void toSnakeCase_withNull_returnsNull() {
        assertThat(scanner.toSnakeCase(null)).isNull();
    }

    @Test
    void toSnakeCase_withEmpty_returnsEmpty() {
        assertThat(scanner.toSnakeCase("")).isEmpty();
    }

    @Test
    void toSnakeCase_withAlreadySnakeCase_returnsUnchanged() {
        assertThat(scanner.toSnakeCase("user_account")).isEqualTo("user_account");
    }

    @Test
    void toSnakeCase_withConsecutiveCapitals_handlesCorrectly() {
        assertThat(scanner.toSnakeCase("HTTPSConnection")).isEqualTo("https_connection");
        assertThat(scanner.toSnakeCase("XMLParser")).isEqualTo("xml_parser");
    }

    // ==================== Test Scanner Implementation ====================

    /**
     * Concrete implementation for testing AbstractJavaParserScanner methods.
     */
    private static class TestJavaParserScanner extends AbstractJavaParserScanner {

        @Override
        public String getId() {
            return "test-javaparser-scanner";
        }

        @Override
        public String getDisplayName() {
            return "Test JavaParser Scanner";
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("java");
        }

        @Override
        public Set<String> getSupportedFilePatterns() {
            return Set.of("**/*.java");
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public boolean appliesTo(ScanContext context) {
            return true;
        }

        @Override
        public ScanResult scan(ScanContext context) {
            return emptyResult();
        }

        // Expose protected methods for testing
        @Override
        public Optional<CompilationUnit> parseJavaFile(Path file) throws IOException {
            return super.parseJavaFile(file);
        }

        @Override
        public List<ClassOrInterfaceDeclaration> findClasses(CompilationUnit cu) {
            return super.findClasses(cu);
        }

        @Override
        public boolean hasAnnotation(ClassOrInterfaceDeclaration clazz, String annotationName) {
            return super.hasAnnotation(clazz, annotationName);
        }

        @Override
        public String getAnnotationAttribute(ClassOrInterfaceDeclaration clazz, String annotationName, String attributeName) {
            return super.getAnnotationAttribute(clazz, annotationName, attributeName);
        }

        @Override
        public String getPackageName(CompilationUnit cu) {
            return super.getPackageName(cu);
        }

        @Override
        public String getFullyQualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration clazz) {
            return super.getFullyQualifiedName(cu, clazz);
        }

        @Override
        public List<MethodDeclaration> findMethodsWithAnnotation(ClassOrInterfaceDeclaration clazz, String annotationName) {
            return super.findMethodsWithAnnotation(clazz, annotationName);
        }

        @Override
        public boolean hasAnnotation(MethodDeclaration method, String annotationName) {
            return super.hasAnnotation(method, annotationName);
        }

        @Override
        public String getAnnotationAttribute(MethodDeclaration method, String annotationName, String attributeName) {
            return super.getAnnotationAttribute(method, annotationName, attributeName);
        }

        @Override
        public List<FieldDeclaration> findFields(ClassOrInterfaceDeclaration clazz) {
            return super.findFields(clazz);
        }

        @Override
        public boolean hasAnnotation(FieldDeclaration field, String annotationName) {
            return super.hasAnnotation(field, annotationName);
        }

        @Override
        public String getAnnotationAttribute(FieldDeclaration field, String annotationName, String attributeName) {
            return super.getAnnotationAttribute(field, annotationName, attributeName);
        }

        @Override
        public String cleanStringLiteral(String literal) {
            return super.cleanStringLiteral(literal);
        }

        @Override
        public String toSnakeCase(String camelCase) {
            return super.toSnakeCase(camelCase);
        }
    }
}
