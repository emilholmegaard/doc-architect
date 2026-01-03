#!/bin/bash
# test-dotnet-eshoponcontainers.sh
# Tests DocArchitect against eShopOnContainers microservices reference architecture

set -e

PROJECT_DIR="test-projects/eshoponcontainers"

echo "=========================================="
echo ".NET Microservices Reference Architecture"
echo "Project: eShopOnContainers"
echo "=========================================="

# Clone or update
# NOTE: The old repo moved to dotnet/eShop in November 2023
# Old URL: https://github.com/dotnet-architecture/eShopOnContainers (archived, only README)
# New URL: https://github.com/dotnet/eShop
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating eShop..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning eShop (formerly eShopOnContainers)..."
    mkdir -p test-projects
    # Clone from the NEW active repository
    git clone --depth 1 https://github.com/dotnet/eShop.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "eShop"
  version: "8.0"
  description: ".NET Microservices Reference Architecture (formerly eShopOnContainers)"

repositories:
  - name: "eshoponcontainers"
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
echo "Running DocArchitect on eShopOnContainers..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/eshopcontainers"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/eshoponcontainers:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "Validating scan results..."

# Validate that scanners actually found something
INDEX_FILE="output/eshoponcontainers/index.md"
if [ ! -f "$INDEX_FILE" ]; then
    echo "✗ FAIL: index.md not generated"
    exit 1
fi

# Extract component count from index.md
COMPONENT_COUNT=$(grep -E "^\| Components \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")
DEPENDENCY_COUNT=$(grep -E "^\| Dependencies \| [0-9]+ \|$" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")

echo "  Components found: $COMPONENT_COUNT"
echo "  Dependencies found: $DEPENDENCY_COUNT"

# eShop is a large .NET microservices application - should have at least SOME findings
if [ "$COMPONENT_COUNT" -eq 0 ] && [ "$DEPENDENCY_COUNT" -eq 0 ]; then
    echo ""
    echo "✗ FAIL: No components or dependencies found!"
    echo "  This likely means:"
    echo "  - Repository structure changed (source not in root directory)"
    echo "  - Repository was not cloned correctly"
    echo "  - Scanners failed to detect .NET files"
    echo ""
    echo "  Check that $PROJECT_DIR contains .cs files"
    exit 1
fi

echo ""
echo "✓ eShop scan complete and validated."
echo "  Results: output/eshoponcontainers/"
echo ""
echo "Expected outputs:"
echo "  - 10+ microservices components (Catalog, Basket, Ordering, Identity, etc.)"
echo "  - 50+ REST API endpoints (HTTP APIs for each microservice)"
echo "  - 30+ Entity Framework entities (domain models)"
echo "  - 15+ message flows (event-driven integration events)"
echo "  - gRPC service definitions (inter-service communication)"
echo "  - Event bus patterns (RabbitMQ/Azure Service Bus)"
echo ""
