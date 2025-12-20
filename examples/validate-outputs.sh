#!/bin/bash
# validate-outputs.sh
# Validates DocArchitect output quality against real-world projects

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

validate_output() {
    local name=$1
    local dir=$2
    local min_endpoints=$3
    local min_entities=$4

    echo ""
    echo "=========================================="
    echo "Validating: $name"
    echo "=========================================="

    # Check if output directory exists
    if [ ! -d "$dir" ]; then
        echo -e "${RED}✗ FAIL: Output directory missing: $dir${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
        return 1
    fi
    echo -e "${GREEN}✓ Output directory exists${NC}"
    ((TOTAL_CHECKS++))
    ((PASSED_CHECKS++))

    # Check for index.md
    if [ -f "$dir/index.md" ]; then
        echo -e "${GREEN}✓ index.md exists${NC}"
        ((TOTAL_CHECKS++))
        ((PASSED_CHECKS++))
    else
        echo -e "${RED}✗ FAIL: Missing index.md${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Check for dependencies directory
    if [ -d "$dir/dependencies" ]; then
        echo -e "${GREEN}✓ dependencies/ directory exists${NC}"
        ((TOTAL_CHECKS++))
        ((PASSED_CHECKS++))

        # Count dependency files
        local dep_count=$(find "$dir/dependencies" -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
        echo "  Found $dep_count dependency files"
    else
        echo -e "${YELLOW}⚠ WARN: Missing dependencies/ directory${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Check for api directory
    if [ -d "$dir/api" ]; then
        echo -e "${GREEN}✓ api/ directory exists${NC}"
        ((TOTAL_CHECKS++))
        ((PASSED_CHECKS++))

        # Count endpoints
        local endpoints=$(grep -r "Method.*Path\|endpoint\|@.*Mapping" "$dir/api/"*.md 2>/dev/null | wc -l | tr -d ' ')
        echo "  Found approximately $endpoints endpoint references"

        if [ "$endpoints" -ge "$min_endpoints" ]; then
            echo -e "${GREEN}✓ Endpoint count ($endpoints) meets minimum ($min_endpoints)${NC}"
            ((TOTAL_CHECKS++))
            ((PASSED_CHECKS++))
        else
            echo -e "${YELLOW}⚠ WARN: Endpoint count ($endpoints) below minimum ($min_endpoints)${NC}"
            ((TOTAL_CHECKS++))
            ((FAILED_CHECKS++))
        fi
    else
        echo -e "${YELLOW}⚠ WARN: Missing api/ directory${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Check for data directory (entities)
    if [ -d "$dir/data" ]; then
        echo -e "${GREEN}✓ data/ directory exists${NC}"
        ((TOTAL_CHECKS++))
        ((PASSED_CHECKS++))

        # Count entities
        local entities=$(grep -r "erDiagram\|Entity\|Table\|class.*:" "$dir/data/"*.md 2>/dev/null | wc -l | tr -d ' ')
        echo "  Found approximately $entities entity references"

        if [ "$entities" -ge "$min_entities" ]; then
            echo -e "${GREEN}✓ Entity count ($entities) meets minimum ($min_entities)${NC}"
            ((TOTAL_CHECKS++))
            ((PASSED_CHECKS++))
        else
            echo -e "${YELLOW}⚠ WARN: Entity count ($entities) below minimum ($min_entities)${NC}"
            ((TOTAL_CHECKS++))
            ((FAILED_CHECKS++))
        fi
    else
        echo -e "${YELLOW}⚠ WARN: Missing data/ directory${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    # Check for Mermaid diagrams
    local mermaid_count=$(grep -r "```mermaid" "$dir"/*.md 2>/dev/null | wc -l | tr -d ' ')
    if [ "$mermaid_count" -gt 0 ]; then
        echo -e "${GREEN}✓ Found $mermaid_count Mermaid diagrams${NC}"
        ((TOTAL_CHECKS++))
        ((PASSED_CHECKS++))
    else
        echo -e "${YELLOW}⚠ WARN: No Mermaid diagrams found${NC}"
        ((TOTAL_CHECKS++))
        ((FAILED_CHECKS++))
    fi

    echo ""
    echo "✓ $name validation complete"
}

echo "=========================================="
echo "DocArchitect Output Validation"
echo "=========================================="

# Validate each project
# Format: validate_output "Name" "path" min_endpoints min_entities
validate_output "PiggyMetrics (Spring Boot)" "output/piggymetrics" 10 5
validate_output "eShopOnWeb (.NET)" "output/eshopweb" 10 15
validate_output "FastAPI" "output/fastapi" 15 3

# Summary
echo ""
echo "=========================================="
echo "Validation Summary"
echo "=========================================="
echo "Total checks: $TOTAL_CHECKS"
echo -e "${GREEN}Passed: $PASSED_CHECKS${NC}"
echo -e "${RED}Failed: $FAILED_CHECKS${NC}"

if [ $FAILED_CHECKS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ All validations passed!${NC}"
    exit 0
else
    PASS_RATE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
    echo ""
    echo -e "${YELLOW}⚠ Some validations failed (${PASS_RATE}% pass rate)${NC}"
    exit 1
fi
