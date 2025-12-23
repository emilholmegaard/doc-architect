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
run_test "Spring Boot Microservices" "./test-spring-microservices.sh"
run_test ".NET Solution" "./test-dotnet-solution.sh"
run_test "Python FastAPI" "./test-python-fastapi.sh"

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
echo "  - output/piggymetrics/  (Spring Boot)"
echo "  - output/eshopweb/      (.NET)"
echo "  - output/fastapi/       (Python)"
echo ""
echo "Verify each output contains:"
echo "  - index.md with navigation"
echo "  - dependencies/ with graphs"
echo "  - api/ with endpoint documentation"
echo "  - data/ with entity diagrams"
echo ""

# Exit with error if any tests failed
exit $TESTS_FAILED
