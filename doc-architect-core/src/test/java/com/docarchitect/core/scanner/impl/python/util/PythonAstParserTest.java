package com.docarchitect.core.scanner.impl.python.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonAstParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseSimpleClass() throws Exception {
        String python = "class User:\n    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals("User", classes.get(0).name);
        assertTrue(classes.get(0).baseClasses.isEmpty());
    }

    @Test
    void testParseClassWithBaseClass() throws Exception {
        String python = "class User(Model):\n    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals("User", classes.get(0).name);
        assertEquals(1, classes.get(0).baseClasses.size());
        assertTrue(classes.get(0).baseClasses.contains("Model"));
    }

    @Test
    void testParseClassWithMultipleBaseClasses() throws Exception {
        String python = "class User(Model, TimestampMixin):\n    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals("User", classes.get(0).name);
        assertEquals(2, classes.get(0).baseClasses.size());
        assertTrue(classes.get(0).baseClasses.contains("Model"));
        assertTrue(classes.get(0).baseClasses.contains("TimestampMixin"));
    }

    @Test
    void testParseClassWithFields() throws Exception {
        String python = "class User:\n" +
            "    name = 'John'\n" +
            "    age = 25\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(2, classes.get(0).fields.size());

        PythonAstParser.PythonField nameField = classes.get(0).fields.stream()
            .filter(f -> f.name.equals("name"))
            .findFirst()
            .orElse(null);
        assertNotNull(nameField);
        assertEquals("'John'", nameField.value);
    }

    @Test
    void testParseClassWithTypeAnnotations() throws Exception {
        String python = "class User:\n" +
            "    name: str = 'John'\n" +
            "    age: int = 25\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(2, classes.get(0).fields.size());

        PythonAstParser.PythonField nameField = classes.get(0).fields.stream()
            .filter(f -> f.name.equals("name"))
            .findFirst()
            .orElse(null);
        assertNotNull(nameField);
        assertEquals("str", nameField.type);

        PythonAstParser.PythonField ageField = classes.get(0).fields.stream()
            .filter(f -> f.name.equals("age"))
            .findFirst()
            .orElse(null);
        assertNotNull(ageField);
        assertEquals("int", ageField.type);
    }

    @Test
    void testParseClassWithDecorators() throws Exception {
        String python = "@dataclass\n" +
            "@decorator2\n" +
            "class User:\n" +
            "    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(2, classes.get(0).decorators.size());
        assertTrue(classes.get(0).decorators.contains("dataclass"));
        assertTrue(classes.get(0).decorators.contains("decorator2"));
    }

    @Test
    void testParseDjangoModel() throws Exception {
        String python = "from django.db import models\n" +
            "\n" +
            "class User(models.Model):\n" +
            "    username = models.CharField(max_length=100)\n" +
            "    email = models.EmailField()\n" +
            "    is_active = models.BooleanField(default=True)\n" +
            "    created_at = models.DateTimeField(auto_now_add=True)\n" +
            "    author = models.ForeignKey(User, on_delete=models.CASCADE)\n";
        Path file = createTempFile("models.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        PythonAstParser.PythonClass user = classes.get(0);
        assertEquals("User", user.name);
        assertTrue(user.inheritsFrom("models.Model"));
        assertEquals(5, user.fields.size());

        // Check specific fields
        PythonAstParser.PythonField username = user.fields.stream()
            .filter(f -> f.name.equals("username"))
            .findFirst()
            .orElse(null);
        assertNotNull(username);
        assertTrue(username.type.contains("CharField"));
    }

    @Test
    void testParseMultipleClasses() throws Exception {
        String python = "class User:\n" +
            "    pass\n" +
            "\n" +
            "class Post:\n" +
            "    title = 'Hello'\n" +
            "\n" +
            "class Comment:\n" +
            "    content = 'Nice'\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(3, classes.size());
        assertEquals("User", classes.get(0).name);
        assertEquals("Post", classes.get(1).name);
        assertEquals("Comment", classes.get(2).name);
    }

    @Test
    void testParseClassWithoutFields() throws Exception {
        String python = "class Empty:\n    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(0, classes.get(0).fields.size());
    }

    @Test
    void testParseClassWithMetaClass() throws Exception {
        String python = "class User:\n" +
            "    name = 'John'\n" +
            "    \n" +
            "    class Meta:\n" +
            "        db_table = 'users'\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(2, classes.size());
        // Meta should not be in fields
        assertFalse(classes.get(0).fields.stream().anyMatch(f -> f.name.equals("Meta")));
    }

    @Test
    void testInheritsFromMethod() throws Exception {
        String python = "class User(models.Model):\n    pass\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        PythonAstParser.PythonClass user = classes.get(0);
        
        assertTrue(user.inheritsFrom("models.Model"));
        assertTrue(user.inheritsFrom("Model"));
        assertFalse(user.inheritsFrom("SomeOtherClass"));
    }

    @Test
    void testParseEmptyFile() throws Exception {
        String python = "";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(0, classes.size());
    }

    @Test
    void testParseClassWithComments() throws Exception {
        String python = "# This is a comment\n" +
            "class User:\n" +
            "    # Field comment\n" +
            "    name = 'John'  # inline comment\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(1, classes.get(0).fields.size());
    }

    @Test
    void testParseClassWithMultilineStrings() throws Exception {
        String python = "class User:\n" +
            "    \"\"\"\n" +
            "    User model class.\n" +
            "    \"\"\"\n" +
            "    name = 'John'\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(1, classes.get(0).fields.size());
    }

    @Test
    void testParseNestedClasses() throws Exception {
        String python = "class Outer:\n" +
            "    value = 1\n" +
            "    \n" +
            "    class Inner:\n" +
            "        inner_value = 2\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        // Should find at least Outer class
        assertTrue(classes.stream().anyMatch(c -> c.name.equals("Outer")));
    }

    @Test
    void testFieldEquality() {
        PythonAstParser.PythonField field1 = new PythonAstParser.PythonField("name", "str", "value", List.of());
        PythonAstParser.PythonField field2 = new PythonAstParser.PythonField("name", "str", "other", List.of());
        PythonAstParser.PythonField field3 = new PythonAstParser.PythonField("age", "int", "value", List.of());

        assertEquals(field1, field2);
        assertNotEquals(field1, field3);
    }

    @Test
    void testPythonClassToString() throws Exception {
        String python = "class User(Model):\n    name = 'John'\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        String toString = classes.get(0).toString();
        assertNotNull(toString);
        assertTrue(toString.contains("User"));
    }

    @Test
    void testExtractTypeFromComplexValue() throws Exception {
        String python = "class User:\n" +
            "    profile = models.OneToOneField('Profile', on_delete=models.CASCADE)\n" +
            "    tags = models.ManyToManyField('Tag', related_name='users')\n";
        Path file = createTempFile("test.py", python);

        List<PythonAstParser.PythonClass> classes = PythonAstParser.parseFile(file);

        assertEquals(1, classes.size());
        assertEquals(2, classes.get(0).fields.size());

        PythonAstParser.PythonField profile = classes.get(0).fields.stream()
            .filter(f -> f.name.equals("profile"))
            .findFirst()
            .orElse(null);
        assertNotNull(profile);
        assertTrue(profile.type.contains("OneToOneField"));
    }

    private Path createTempFile(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.write(file, content.getBytes());
        return file;
    }
}
