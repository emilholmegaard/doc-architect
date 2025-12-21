#!/bin/bash
# validate-outputs.sh
# Validates DocArchitect output quality against real-world projects
#
# This script validates that scanners actually found the expected content,
# not just that output directories exist. It should catch critical scanner
# failures like:
# - NuGetDependencyScanner returning 0 dependencies
# - FastAPIScanner returning 0 endpoints
# - SQLAlchemyScanner finding wrong entities
# - EntityFrameworkScanner marking all fields as PK

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
CRITICAL_FAILURES=0

# Helper function to validate minimum count from index.md
validate_min_count() {
    local project=$1
    local metric=$2
    local expected=$3
    local index_file="output/$project/index.md"

    ((TOTAL_CHECKS++))

    if [ ! -f "$index_file" ]; then
        echo -e "${RED}✗ CRITICAL: Missing $index_file${NC}"
        ((FAILED_CHECKS++))
        ((CRITICAL_FAILURES++))
        return 1
    fi

    # Extract count from index.md table (e.g., "| Components | 9 |")
    local metric_pattern
    case $metric in
        "components") metric_pattern="Components" ;;
        "dependencies") metric_pattern="Dependencies" ;;
        "api_endpoints") metric_pattern="API Endpoints" ;;
        "data_entities") metric_pattern="Data Entities" ;;
        *) metric_pattern="$metric" ;;
    esac

    local actual=$(grep "| $metric_pattern |" "$index_file" | awk -F'|' '{print $3}' | tr -d ' ')

    if [ -z "$actual" ]; then
        echo -e "${RED}✗ FAIL: Could not extract $metric count from $index_file${NC}"
        ((FAILED_CHECKS++))
        return 1
    fi

    if [ "$actual" -ge "$expected" ]; then
        echo -e "${GREEN}✓ $metric_pattern: $actual >= $expected (minimum)${NC}"
        ((PASSED_CHECKS++))
        return 0
    else
        echo -e "${RED}✗ FAIL: $metric_pattern: $actual < $expected (minimum) - Expected at least $expected${NC}"
        ((FAILED_CHECKS++))
        if [ "$actual" -eq 0 ]; then
            echo -e "${RED}  ⚠ CRITICAL: Scanner returned 0 results (likely broken)${NC}"
            ((CRITICAL_FAILURES++))
        fi
        return 1
    fi
}

# Helper function to check if content exists in file
check_contains() {
    local file=$1
    local pattern=$2
    local description=$3

    ((TOTAL_CHECKS++))

    if [ ! -f "$file" ]; then
        echo -e "${RED}✗ FAIL: File not found: $file${NC}"
        ((FAILED_CHECKS++))
        return 1
    fi

    if grep -q "$pattern" "$file"; then
        echo -e "${GREEN}✓ Contains: $description${NC}"
        ((PASSED_CHECKS++))
        return 0
    else
        echo -e "${RED}✗ FAIL: Missing expected content: $description${NC}"
        ((FAILED_CHECKS++))
        return 1
    fi
}

# Helper function to check if content does NOT exist in file
check_not_contains() {
    local file=$1
    local pattern=$2
    local description=$3

    ((TOTAL_CHECKS++))

    if [ ! -f "$file" ]; then
        echo -e "${YELLOW}⚠ WARN: File not found: $file (skipping check)${NC}"
        return 0
    fi

    if ! grep -q "$pattern" "$file"; then
        echo -e "${GREEN}✓ Does not contain: $description${NC}"
        ((PASSED_CHECKS++))
        return 0
    else
        echo -e "${RED}✗ FAIL: Found unexpected content: $description${NC}"
        ((FAILED_CHECKS++))
        return 1
    fi
}

# Helper function to validate ER diagram has reasonable PK markers
validate_er_diagram_quality() {
    local project=$1
    local er_file="output/$project/er-diagram.md"

    echo ""
    echo -e "${BLUE}Validating ER Diagram Quality${NC}"

    if [ ! -f "$er_file" ]; then
        echo -e "${YELLOW}⚠ WARN: ER diagram not found (skipping quality checks)${NC}"
        return 0
    fi

    # Extract entity blocks and check PK markers
    local entities=$(grep -E "^  [A-Z_]+ \{" "$er_file" | wc -l | tr -d ' ')

    if [ "$entities" -eq 0 ]; then
        echo -e "${YELLOW}⚠ WARN: No entities found in ER diagram${NC}"
        return 0
    fi

    echo "  Found $entities entities in ER diagram"

    # Count total fields and PK-marked fields
    local total_fields=$(grep -E "    [A-Z]+ .+ PK|    [A-Z]+ .+ [^P][^K]" "$er_file" | wc -l | tr -d ' ')
    local pk_fields=$(grep -E " PK$" "$er_file" | wc -l | tr -d ' ')

    if [ "$total_fields" -eq 0 ]; then
        echo -e "${YELLOW}⚠ WARN: No fields found in ER diagram${NC}"
        return 0
    fi

    local pk_percentage=$((pk_fields * 100 / total_fields))

    echo "  Total fields: $total_fields, PK fields: $pk_fields ($pk_percentage%)"

    ((TOTAL_CHECKS++))

    # If more than 50% of fields are marked as PK, something is wrong
    if [ "$pk_percentage" -gt 50 ]; then
        echo -e "${RED}✗ FAIL: Too many fields marked as PK ($pk_percentage%) - likely incorrect${NC}"
        echo -e "${RED}  Expected: ~10-20% of fields should be PK (1 per entity typically)${NC}"
        ((FAILED_CHECKS++))
        ((CRITICAL_FAILURES++))
        return 1
    else
        echo -e "${GREEN}✓ PK percentage ($pk_percentage%) looks reasonable${NC}"
        ((PASSED_CHECKS++))
        return 0
    fi
}

# Validate PiggyMetrics (Spring Boot)
validate_piggymetrics() {
    echo ""
    echo "=========================================="
    echo "Validating: PiggyMetrics (Spring Boot)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "piggymetrics" "components" 9
    validate_min_count "piggymetrics" "dependencies" 20
    validate_min_count "piggymetrics" "api_endpoints" 15
    validate_min_count "piggymetrics" "data_entities" 5  # MongoDB entities

    # Content validation - Check for known endpoints
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    check_contains "output/piggymetrics/api-catalog.md" "GET /recipients/current" "Notification endpoint"
    check_contains "output/piggymetrics/api-catalog.md" "POST /users" "User creation endpoint"
    check_contains "output/piggymetrics/api-catalog.md" "GET /current" "Current account endpoint"

    # Content validation - Check for MongoDB entities
    echo ""
    echo -e "${BLUE}Validating Data Entities${NC}"
    if [ -f "output/piggymetrics/er-diagram.md" ]; then
        check_contains "output/piggymetrics/er-diagram.md" "DATAPOINT\|DataPoint" "DataPoint entity"
        check_contains "output/piggymetrics/er-diagram.md" "USER\|User" "User entity"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate eShopOnWeb (.NET)
validate_eshopweb() {
    echo ""
    echo "=========================================="
    echo "Validating: eShopOnWeb (.NET)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "eshopweb" "components" 3
    validate_min_count "eshopweb" "dependencies" 15  # NuGet packages
    validate_min_count "eshopweb" "api_endpoints" 10
    validate_min_count "eshopweb" "data_entities" 7

    # Content validation - Check for known controllers
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/eshopweb/api-catalog.md" ]; then
        check_contains "output/eshopweb/api-catalog.md" "OrderController\|/api/order" "Order API"
        check_contains "output/eshopweb/api-catalog.md" "CatalogController\|/api/catalog" "Catalog API"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for known entities
    echo ""
    echo -e "${BLUE}Validating Data Entities${NC}"
    if [ -f "output/eshopweb/er-diagram.md" ]; then
        check_contains "output/eshopweb/er-diagram.md" "ORDER\|Order" "Order entity"
        check_contains "output/eshopweb/er-diagram.md" "CATALOGITEM\|CatalogItem" "CatalogItem entity"
        check_contains "output/eshopweb/er-diagram.md" "BASKET\|Basket" "Basket entity"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram PK markers
    validate_er_diagram_quality "eshopweb"

    # Dependency validation - Check for known NuGet packages
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/eshopweb/dependency-graph.md" ]; then
        check_contains "output/eshopweb/dependency-graph.md" "MediatR\|EntityFrameworkCore\|AutoMapper" "Common NuGet packages"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate FastAPI
validate_fastapi() {
    echo ""
    echo "=========================================="
    echo "Validating: FastAPI (Python)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "fastapi" "components" 1
    validate_min_count "fastapi" "dependencies" 15  # Poetry dependencies
    validate_min_count "fastapi" "api_endpoints" 15
    validate_min_count "fastapi" "data_entities" 2  # User and Item tables ONLY

    # Content validation - Check for known routes
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/fastapi/api-catalog.md" ]; then
        check_contains "output/fastapi/api-catalog.md" "/users\|/items" "User/Item routes"
        check_contains "output/fastapi/api-catalog.md" "GET\|POST" "HTTP methods"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for actual SQLModel tables
    echo ""
    echo -e "${BLUE}Validating Data Entities (SQLModel)${NC}"
    if [ -f "output/fastapi/er-diagram.md" ]; then
        check_contains "output/fastapi/er-diagram.md" "USER" "User table"
        check_contains "output/fastapi/er-diagram.md" "ITEM" "Item table"

        # CRITICAL: Should NOT contain Pydantic schemas (these are NOT database tables)
        check_not_contains "output/fastapi/er-diagram.md" "USER_CREATE\|USERCREATE" "UserCreate schema (NOT a table)"
        check_not_contains "output/fastapi/er-diagram.md" "USER_UPDATE\|USERUPDATE" "UserUpdate schema (NOT a table)"
        check_not_contains "output/fastapi/er-diagram.md" "ITEM_CREATE\|ITEMCREATE" "ItemCreate schema (NOT a table)"
        check_not_contains "output/fastapi/er-diagram.md" "ITEM_UPDATE\|ITEMUPDATE" "ItemUpdate schema (NOT a table)"
        check_not_contains "output/fastapi/er-diagram.md" "TOKEN\|MESSAGE" "Token/Message schemas (NOT tables)"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram entity count
    echo ""
    echo -e "${BLUE}Validating Entity Count${NC}"
    if [ -f "output/fastapi/er-diagram.md" ]; then
        local entity_count=$(grep -E "^  [A-Z_]+ \{" "output/fastapi/er-diagram.md" | wc -l | tr -d ' ')

        ((TOTAL_CHECKS++))

        if [ "$entity_count" -eq 2 ]; then
            echo -e "${GREEN}✓ Correct entity count: $entity_count (User and Item only)${NC}"
            ((PASSED_CHECKS++))
        elif [ "$entity_count" -gt 2 ]; then
            echo -e "${RED}✗ FAIL: Too many entities: $entity_count (expected 2)${NC}"
            echo -e "${RED}  Likely detecting Pydantic schemas as tables${NC}"
            ((FAILED_CHECKS++))
            ((CRITICAL_FAILURES++))
        else
            echo -e "${RED}✗ FAIL: Too few entities: $entity_count (expected 2)${NC}"
            ((FAILED_CHECKS++))
        fi
    fi
}

# Main execution
echo "=========================================="
echo "DocArchitect Output Validation"
echo "=========================================="
echo "This script validates scanner effectiveness"
echo "against production open-source projects."
echo ""

validate_piggymetrics
validate_eshopweb
validate_fastapi

# Summary
echo ""
echo "=========================================="
echo "Validation Summary"
echo "=========================================="
echo "Total checks: $TOTAL_CHECKS"
echo -e "${GREEN}Passed: $PASSED_CHECKS${NC}"
echo -e "${RED}Failed: $FAILED_CHECKS${NC}"

if [ $CRITICAL_FAILURES -gt 0 ]; then
    echo -e "${RED}Critical failures: $CRITICAL_FAILURES (scanners returning 0 results or wrong data)${NC}"
fi

if [ $FAILED_CHECKS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ All validations passed!${NC}"
    echo -e "${GREEN}  DocArchitect scanners are working correctly.${NC}"
    exit 0
else
    PASS_RATE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
    echo ""
    echo -e "${RED}✗ Validation failed (${PASS_RATE}% pass rate)${NC}"
    echo ""
    echo "Common issues:"
    echo "  - NuGetDependencyScanner not finding .NET packages (issue #60)"
    echo "  - FastAPIScanner not finding Python endpoints (issue #61)"
    echo "  - SQLAlchemyScanner detecting Pydantic schemas as tables (issue #62)"
    echo "  - AspNetCoreApiScanner missing most endpoints (issue #63)"
    echo "  - EntityFrameworkScanner marking all fields as PK (issue #64)"
    echo "  - JpaEntityScanner not detecting MongoDB entities (issue #65)"
    echo ""
    echo "See REAL_WORLD_TEST_ANALYSIS.md for detailed analysis."
    exit 1
fi
