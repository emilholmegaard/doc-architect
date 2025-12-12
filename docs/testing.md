# Testing Guide

This document outlines the testing infrastructure for DocArchitect.

## Test Structure

```
src/test/java/
└── com/docarchitect/
    ├── ExampleTest.java              # Unit tests
    └── ExampleIntegrationTest.java   # Integration tests
```

## Running Tests

### Unit Tests Only
```bash
./mvnw test
```

### Integration Tests Only
```bash
./mvnw verify -DskipUnitTests
```

### All Tests with Coverage
```bash
./mvnw clean verify
```

### View Coverage Report
```bash
./mvnw jacoco:report
open target/site/jacoco/index.html
```

## Test Naming Conventions

- **Unit tests**: `*Test.java` or `*Tests.java`
- **Integration tests**: `*IntegrationTest.java` or `*IT.java`

## Writing Tests

### Unit Test Example
```java
@DisplayName("Component Name Tests")
class ComponentTest {
    
    @Test
    @DisplayName("Should do something specific")
    void shouldDoSomethingSpecific() {
        // Arrange
        var component = new Component();
        
        // Act
        var result = component.doSomething();
        
        // Assert
        assertThat(result).isNotNull();
    }
}
```

### Integration Test Example
```java
@DisplayName("End-to-End Workflow Tests")
class WorkflowIntegrationTest {
    
    @Test
    @DisplayName("Should complete full workflow")
    void shouldCompleteFullWorkflow() {
        // Test complete workflow here
    }
}
```

## Test Coverage Goals

- **Overall coverage**: 60%+ (configured in pom.xml)
- **Core logic**: 80%+
- **Utils/helpers**: 90%+

## CI/CD Integration

Tests run automatically on:
- Every push to main/develop branches
- Every pull request
- Nightly scheduled runs

### GitHub Actions Workflow

See `.github/workflows/ci.yml` for the complete CI/CD setup including:
- Unit tests
- Integration tests
- Code coverage reporting
- Security scanning

## Mocking

Use Mockito for mocking dependencies:

```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    
    @Mock
    private Dependency dependency;
    
    @InjectMocks
    private Service service;
    
    @Test
    void shouldUseMockedDependency() {
        when(dependency.getData()).thenReturn("mocked");
        assertThat(service.process()).isEqualTo("mocked");
    }
}
```

## Best Practices

1. **Arrange-Act-Assert**: Structure tests clearly
2. **Descriptive names**: Use `@DisplayName` for readable test descriptions
3. **One assertion per test**: Keep tests focused
4. **Test behavior, not implementation**: Focus on outcomes
5. **Use AssertJ**: For fluent, readable assertions
6. **Mock external dependencies**: Keep tests isolated and fast
7. **Test edge cases**: Include boundary conditions and error cases

## Debugging Tests

### Run specific test class
```bash
./mvnw test -Dtest=ExampleTest
```

### Run specific test method
```bash
./mvnw test -Dtest=ExampleTest#shouldPassBasicAssertion
```

### Debug mode
```bash
./mvnw test -Dmaven.surefire.debug
```

## Performance Testing

For performance-critical components, use JMH (Java Microbenchmark Harness):

```xml
<!-- Add to pom.xml if needed -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

## Test Data

Store test fixtures in `src/test/resources/`:
```
src/test/resources/
├── fixtures/
│   ├── example-pom.xml
│   └── sample-config.yaml
└── application-test.properties
```

## Continuous Improvement

- Review and update tests with each PR
- Aim to increase coverage over time
- Refactor tests when implementation changes
- Remove obsolete tests promptly
