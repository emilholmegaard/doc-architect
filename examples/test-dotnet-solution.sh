#!/bin/bash
# test-dotnet-solution.sh
# Tests DocArchitect against eShopOnWeb .NET application

set -e

PROJECT_DIR="test-projects/eShopOnWeb"

echo "=========================================="
echo ".NET Microservices Test"
echo "Project: eShopOnWeb"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating eShopOnWeb..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning eShopOnWeb..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/dotnet-architecture/eShopOnWeb.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "eShopOnWeb"
  version: "3.0.0"
  description: "Reference .NET Application with Clean Architecture"

repositories:
  - name: "eshop"
    path: "."

scanners:
  enabled:
    - nuget-dependencies
    - aspnetcore-rest
    - entity-framework
    - sql-migration

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
echo "Running DocArchitect on eShopOnWeb..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/eshopweb"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/eshopweb:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ eShopOnWeb scan complete."
echo "  Results: output/eshopweb/"
echo ""
echo "Expected outputs:"
echo "  - 5+ projects (Web, Infrastructure, ApplicationCore, PublicApi)"
echo "  - 30+ NuGet dependencies"
echo "  - 15+ API endpoints"
echo "  - 20+ EF Core entities"
echo "  - Clean architecture layer dependencies"
echo ""
