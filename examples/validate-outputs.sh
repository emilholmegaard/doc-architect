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

# Validate Saleor (Python GraphQL)
validate_saleor() {
    echo ""
    echo "=========================================="
    echo "Validating: Saleor (Python GraphQL)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "saleor" "components" 15
    validate_min_count "saleor" "dependencies" 50  # Poetry dependencies
    validate_min_count "saleor" "api_endpoints" 200  # GraphQL operations
    validate_min_count "saleor" "data_entities" 100  # Django ORM models

    # Content validation - Check for GraphQL schema
    echo ""
    echo -e "${BLUE}Validating GraphQL Content${NC}"
    if [ -f "output/saleor/api-catalog.md" ]; then
        check_contains "output/saleor/api-catalog.md" "Query\|Mutation\|Product\|Order" "GraphQL types"
        check_contains "output/saleor/api-catalog.md" "GraphQL\|graphql" "GraphQL schema documentation"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for Django models
    echo ""
    echo -e "${BLUE}Validating Django ORM Entities${NC}"
    if [ -f "output/saleor/er-diagram.md" ]; then
        check_contains "output/saleor/er-diagram.md" "PRODUCT\|Product" "Product entity"
        check_contains "output/saleor/er-diagram.md" "ORDER\|Order" "Order entity"
        check_contains "output/saleor/er-diagram.md" "USER\|User" "User entity"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for key Python packages
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/saleor/dependency-graph.md" ]; then
        check_contains "output/saleor/dependency-graph.md" "graphene\|django\|celery" "Key Python dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate Keycloak (Java)
validate_keycloak() {
    echo ""
    echo "=========================================="
    echo "Validating: Keycloak (Java)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "keycloak" "components" 50
    validate_min_count "keycloak" "dependencies" 100  # Maven dependencies
    validate_min_count "keycloak" "api_endpoints" 100
    validate_min_count "keycloak" "data_entities" 150  # JPA entities

    # Content validation - Check for known REST endpoints
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/keycloak/api-catalog.md" ]; then
        check_contains "output/keycloak/api-catalog.md" "realms\|users\|clients\|roles" "Keycloak API paths"
        check_contains "output/keycloak/api-catalog.md" "GET\|POST\|PUT\|DELETE" "HTTP methods"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for known entities
    echo ""
    echo -e "${BLUE}Validating Data Entities${NC}"
    if [ -f "output/keycloak/er-diagram.md" ]; then
        check_contains "output/keycloak/er-diagram.md" "REALM\|Realm\|CLIENT\|Client\|USER\|User" "Core entities"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for Maven modules
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/keycloak/dependency-graph.md" ]; then
        check_contains "output/keycloak/dependency-graph.md" "quarkus\|hibernate\|jackson" "Key Java dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram PK markers
    validate_er_diagram_quality "keycloak"
}

# Validate eShopOnContainers (.NET Microservices)
validate_eshoponcontainers() {
    echo ""
    echo "=========================================="
    echo "Validating: eShopOnContainers (.NET)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "eshoponcontainers" "components" 10
    validate_min_count "eshoponcontainers" "dependencies" 30  # NuGet packages
    validate_min_count "eshoponcontainers" "api_endpoints" 50
    validate_min_count "eshoponcontainers" "data_entities" 30

    # Content validation - Check for microservice APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/eshoponcontainers/api-catalog.md" ]; then
        check_contains "output/eshoponcontainers/api-catalog.md" "catalog\|basket\|ordering\|identity" "Microservice APIs"
        check_contains "output/eshoponcontainers/api-catalog.md" "GET\|POST\|PUT\|DELETE" "HTTP methods"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for known entities
    echo ""
    echo -e "${BLUE}Validating Data Entities${NC}"
    if [ -f "output/eshoponcontainers/er-diagram.md" ]; then
        check_contains "output/eshoponcontainers/er-diagram.md" "ORDER\|Order\|CATALOG\|Catalog\|BASKET\|Basket" "Core entities"
        check_contains "output/eshoponcontainers/er-diagram.md" "BUYER\|Buyer\|PRODUCT\|Product" "Commerce entities"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for .NET packages
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/eshoponcontainers/dependency-graph.md" ]; then
        check_contains "output/eshoponcontainers/dependency-graph.md" "EntityFrameworkCore\|MediatR\|AutoMapper" "Common .NET packages"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram PK markers
    validate_er_diagram_quality "eshoponcontainers"
}

# Validate Gitea (Go)
validate_gitea() {
    echo ""
    echo "=========================================="
    echo "Validating: Gitea (Go)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "gitea" "components" 1
    validate_min_count "gitea" "dependencies" 100  # Go module dependencies

    # Dependency validation - Check for Go modules
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/gitea/dependency-graph.md" ]; then
        check_contains "output/gitea/dependency-graph.md" "github.com\|golang.org" "Go module dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate Linkerd2 (Go gRPC)
validate_linkerd2() {
    echo ""
    echo "=========================================="
    echo "Validating: Linkerd2 (Go gRPC)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "linkerd2" "components" 10
    validate_min_count "linkerd2" "dependencies" 50  # Go module dependencies

    # Dependency validation - Check for Go modules
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/linkerd2/dependency-graph.md" ]; then
        check_contains "output/linkerd2/dependency-graph.md" "github.com\|golang.org\|k8s.io" "Go/K8s dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate Umbraco CMS (.NET)
validate_umbraco() {
    echo ""
    echo "=========================================="
    echo "Validating: Umbraco CMS (.NET)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "umbraco" "components" 20
    validate_min_count "umbraco" "dependencies" 80  # NuGet packages
    validate_min_count "umbraco" "api_endpoints" 100
    validate_min_count "umbraco" "data_entities" 50

    # Content validation - Check for CMS APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/umbraco/api-catalog.md" ]; then
        check_contains "output/umbraco/api-catalog.md" "content\|media\|member\|backoffice" "CMS API endpoints"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Content validation - Check for CMS entities
    echo ""
    echo -e "${BLUE}Validating Data Entities${NC}"
    if [ -f "output/umbraco/er-diagram.md" ]; then
        check_contains "output/umbraco/er-diagram.md" "CONTENT\|Content\|NODE\|Node\|DOCUMENT\|Document" "CMS entities"
    else
        echo -e "${RED}✗ FAIL: ER diagram not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram PK markers
    validate_er_diagram_quality "umbraco"
}

# Validate Apache Druid (Java)
validate_druid() {
    echo ""
    echo "=========================================="
    echo "Validating: Apache Druid (Java)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "druid" "components" 100
    validate_min_count "druid" "dependencies" 200  # Maven dependencies
    validate_min_count "druid" "api_endpoints" 50

    # Content validation - Check for Druid APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/druid/api-catalog.md" ]; then
        check_contains "output/druid/api-catalog.md" "query\|ingest\|coordinator\|broker" "Druid API endpoints"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for Maven modules
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/druid/dependency-graph.md" ]; then
        check_contains "output/druid/dependency-graph.md" "jackson\|guava\|jetty" "Common Java dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate OrchardCore (.NET Modular CMS)
validate_orchardcore() {
    echo ""
    echo "=========================================="
    echo "Validating: OrchardCore (.NET)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "orchardcore" "components" 30
    validate_min_count "orchardcore" "dependencies" 100  # NuGet packages
    validate_min_count "orchardcore" "api_endpoints" 150
    validate_min_count "orchardcore" "data_entities" 40

    # Content validation - Check for modular CMS APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/orchardcore/api-catalog.md" ]; then
        check_contains "output/orchardcore/api-catalog.md" "content\|module\|tenant\|admin" "Modular CMS endpoints"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Quality validation - Check ER diagram PK markers
    validate_er_diagram_quality "orchardcore"
}

# Validate openHAB Core (Java OSGi)
validate_openhab() {
    echo ""
    echo "=========================================="
    echo "Validating: openHAB Core (Java)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "openhab" "components" 40
    validate_min_count "openhab" "dependencies" 150  # Maven dependencies
    validate_min_count "openhab" "api_endpoints" 50

    # Content validation - Check for home automation APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/openhab/api-catalog.md" ]; then
        check_contains "output/openhab/api-catalog.md" "thing\|item\|binding\|channel" "Home automation endpoints"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for OSGi/Maven
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/openhab/dependency-graph.md" ]; then
        check_contains "output/openhab/dependency-graph.md" "osgi\|eclipse\|jetty" "OSGi dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate GitLab (Ruby Rails)
validate_gitlab() {
    echo ""
    echo "=========================================="
    echo "Validating: GitLab (Ruby)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "gitlab" "components" 50
    validate_min_count "gitlab" "dependencies" 200  # Ruby gems
    validate_min_count "gitlab" "api_endpoints" 500
    validate_min_count "gitlab" "data_entities" 200

    # Content validation - Check for DevOps APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/gitlab/api-catalog.md" ]; then
        check_contains "output/gitlab/api-catalog.md" "project\|pipeline\|merge_request\|issue" "GitLab API endpoints"
        check_contains "output/gitlab/api-catalog.md" "GraphQL\|REST" "API types"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for Ruby gems
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/gitlab/dependency-graph.md" ]; then
        check_contains "output/gitlab/dependency-graph.md" "rails\|grape\|graphql" "Key Ruby gems"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi
}

# Validate Mattermost (Go)
validate_mattermost() {
    echo ""
    echo "=========================================="
    echo "Validating: Mattermost (Go)"
    echo "=========================================="

    # Minimum counts
    validate_min_count "mattermost" "components" 10
    validate_min_count "mattermost" "dependencies" 100  # Go module dependencies
    validate_min_count "mattermost" "api_endpoints" 100

    # Content validation - Check for collaboration APIs
    echo ""
    echo -e "${BLUE}Validating API Content${NC}"
    if [ -f "output/mattermost/api-catalog.md" ]; then
        check_contains "output/mattermost/api-catalog.md" "channel\|user\|team\|post\|websocket" "Collaboration endpoints"
    else
        echo -e "${RED}✗ FAIL: API catalog not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Dependency validation - Check for Go modules
    echo ""
    echo -e "${BLUE}Validating Dependencies${NC}"
    if [ -f "output/mattermost/dependency-graph.md" ]; then
        check_contains "output/mattermost/dependency-graph.md" "github.com\|golang.org" "Go module dependencies"
    else
        echo -e "${RED}✗ FAIL: Dependency graph not generated${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
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
validate_saleor
validate_keycloak
validate_eshoponcontainers
validate_gitea
validate_linkerd2
validate_umbraco
validate_druid
validate_orchardcore
validate_openhab
validate_gitlab
validate_mattermost

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
