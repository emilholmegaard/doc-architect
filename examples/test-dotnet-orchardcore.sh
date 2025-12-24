#!/bin/bash
# test-dotnet-orchardcore.sh
# Tests DocArchitect against OrchardCore modular CMS

set -e

PROJECT_DIR="test-projects/orchardcore"

echo "=========================================="
echo ".NET Modular CMS Test"
echo "Project: OrchardCore"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating OrchardCore..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning OrchardCore..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/OrchardCMS/OrchardCore.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "OrchardCore"
  version: "1.8"
  description: "Modular Multi-Tenant ASP.NET Core CMS"

repositories:
  - name: "orchardcore"
    path: "src"

scanners:
  enabled:
    - nuget-dependencies
    - aspnet-core-api
    - entity-framework

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
echo "Running DocArchitect on OrchardCore..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/orchardcore:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ OrchardCore scan complete."
echo "  Results: output/orchardcore/"
echo ""
echo "Expected outputs:"
echo "  - 30+ components (modular architecture)"
echo "  - 100+ NuGet dependencies"
echo "  - 150+ ASP.NET Core API endpoints"
echo "  - 40+ EF Core entities"
echo "  - Multi-tenant patterns"
echo ""
