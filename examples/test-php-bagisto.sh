#!/bin/bash
# test-php-bagisto.sh
# Tests DocArchitect against Bagisto - Laravel e-commerce platform

set -e

PROJECT_DIR="test-projects/bagisto"

echo "=========================================="
echo "PHP Laravel E-Commerce Platform"
echo "Project: Bagisto"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Bagisto..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Bagisto..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/bagisto/bagisto.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Bagisto"
  version: "2.0"
  description: "Laravel E-Commerce Platform - Open Source Shopping Cart"

repositories:
  - name: "bagisto"
    path: "."

scanners:
  mode: auto

generators:
  default: mermaid
  enabled:
    - mermaid
    - markdown

output:
  directory: "./docs/architecture"
  generateIndex: true
EOF

# Run DocArchitect
echo ""
echo "Running DocArchitect on Bagisto..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/bagisto"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/bagisto:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "Validating scan results..."

# Validate that scanners actually found something
INDEX_FILE="output/bagisto/index.md"
if [ ! -f "$INDEX_FILE" ]; then
    echo "✗ FAIL: index.md not generated"
    exit 1
fi

# Extract counts from index.md
COMPONENT_COUNT=$(grep -E "^\| Components \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")
DEPENDENCY_COUNT=$(grep -E "^\| Dependencies \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")
ENDPOINT_COUNT=$(grep -E "^\| API Endpoints \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")
ENTITY_COUNT=$(grep -E "^\| Data Entities \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")
RELATIONSHIP_COUNT=$(grep -E "^\| Relationships \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")

echo "  Components found: $COMPONENT_COUNT"
echo "  Dependencies found: $DEPENDENCY_COUNT"
echo "  API Endpoints found: $ENDPOINT_COUNT"
echo "  Data Entities found: $ENTITY_COUNT"
echo "  Relationships found: $RELATIONSHIP_COUNT"

# Bagisto is a large Laravel e-commerce application - should have findings
if [ "$COMPONENT_COUNT" -eq 0 ] && [ "$ENTITY_COUNT" -eq 0 ] && [ "$ENDPOINT_COUNT" -eq 0 ]; then
    echo ""
    echo "✗ FAIL: No components, entities, or endpoints found!"
    echo "  This likely means:"
    echo "  - PHP scanners failed to detect Laravel files"
    echo "  - Repository structure changed"
    echo "  - Repository was not cloned correctly"
    echo ""
    echo "  Check that $PROJECT_DIR contains .php files"
    exit 1
fi

echo ""
echo "✓ Bagisto scan complete and validated."
echo "  Results: output/bagisto/"
echo ""
echo "Expected outputs (PHP Laravel E-Commerce):"
echo "  - Composer dependencies (bagisto/bagisto packages)"
echo "  - 50+ Eloquent models (Product, Category, Customer, Order, Cart, etc.)"
echo "  - 100+ Laravel API endpoints (REST APIs for shop, admin, API packages)"
echo "  - Database relationships (e-commerce domain model)"
echo "  - Internal PHP dependencies (services, repositories, controllers)"
echo ""
echo "This tests the following PHP scanners:"
echo "  - composer-dependencies (Composer package manager)"
echo "  - laravel-api (Route definitions)"
echo "  - eloquent-models (Database ORM entities)"
echo "  - php-internal-dependencies (Class dependencies, DI, traits)"
echo ""
