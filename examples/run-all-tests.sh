#!/bin/bash
# run-all-tests.sh
# Master test runner for all real-world project tests

set -e

echo "=========================================="
echo "DocArchitect Real-World Testing Suite"
echo "=========================================="
echo ""

# Create output directory
mkdir -p output

# Track results
TESTS_PASSED=0
TESTS_FAILED=0
FAILED_TESTS=""

# Function to run a test and track results
run_test() {
    local test_name=$1
    local test_script=$2

    echo ""
    echo "Running: $test_name"
    echo "------------------------------------------"

    if bash "$test_script"; then
        ((TESTS_PASSED++))
        echo "✓ $test_name PASSED"
    else
        ((TESTS_FAILED++))
        FAILED_TESTS="$FAILED_TESTS\n  - $test_name"
        echo "✗ $test_name FAILED"
    fi
}

# Run all tests
run_test "Spring Boot Microservices (PiggyMetrics)" "./examples/test-spring-microservices.sh"
run_test ".NET Clean Architecture (eShopOnWeb)" "./examples/test-dotnet-solution.sh"
run_test "Python FastAPI" "./examples/test-python-fastapi.sh"
run_test "Python GraphQL (Saleor)" "./examples/test-python-saleor.sh"
run_test "Java Identity Management (Keycloak)" "./examples/test-java-keycloak.sh"
run_test ".NET Microservices (eShopOnContainers)" "./examples/test-dotnet-eshoponcontainers.sh"
run_test "Go Git Service (Gitea)" "./examples/test-go-gitea.sh"
run_test "Go Service Mesh (Linkerd2)" "./examples/test-go-linkerd2.sh"
run_test ".NET CMS (Umbraco)" "./examples/test-dotnet-umbraco.sh"
run_test "Java Distributed Database (Apache Druid)" "./examples/test-java-druid.sh"
run_test ".NET Modular CMS (OrchardCore)" "./examples/test-dotnet-orchardcore.sh"
run_test "Java OSGi Home Automation (openHAB)" "./examples/test-java-openhab.sh"
run_test "Ruby Rails DevOps (GitLab)" "./examples/test-ruby-gitlab.sh"
run_test "Go Collaboration Platform (Mattermost)" "./examples/test-go-mattermost.sh"

# Summary
echo ""
echo "=========================================="
echo "Test Suite Summary"
echo "=========================================="
echo "Tests passed: $TESTS_PASSED"
echo "Tests failed: $TESTS_FAILED"

if [ $TESTS_FAILED -gt 0 ]; then
    echo ""
    echo "Failed tests:$FAILED_TESTS"
fi

echo ""
echo "Results available in:"
echo "  - output/piggymetrics/        (Spring Boot Microservices)"
echo "  - output/eshopweb/            (.NET Clean Architecture)"
echo "  - output/fastapi/             (Python FastAPI)"
echo "  - output/saleor/              (Python GraphQL)"
echo "  - output/keycloak/            (Java Identity Management)"
echo "  - output/eshoponcontainers/   (.NET Microservices)"
echo "  - output/gitea/               (Go Git Service)"
echo "  - output/linkerd2/            (Go Service Mesh)"
echo "  - output/umbraco/             (.NET CMS)"
echo "  - output/druid/               (Java Distributed Database)"
echo "  - output/orchardcore/         (.NET Modular CMS)"
echo "  - output/openhab/             (Java OSGi Home Automation)"
echo "  - output/gitlab/              (Ruby Rails DevOps)"
echo "  - output/mattermost/          (Go Collaboration)"
echo ""
echo "Verify each output contains:"
echo "  - index.md with navigation"
echo "  - dependencies/ with graphs"
echo "  - api/ with endpoint documentation"
echo "  - data/ with entity diagrams"
echo ""

# Exit with error if any tests failed
exit $TESTS_FAILED
