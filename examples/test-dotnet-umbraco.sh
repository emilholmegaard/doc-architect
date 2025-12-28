#!/bin/bash
# test-dotnet-umbraco.sh
# Tests DocArchitect against Umbraco CMS

set -e

PROJECT_DIR="test-projects/umbraco"

echo "=========================================="
echo ".NET CMS Test"
echo "Project: Umbraco CMS"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Umbraco..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Umbraco..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/umbraco/Umbraco-CMS.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Umbraco CMS"
  version: "13.0"
  description: "Mature .NET Content Management System"

repositories:
  - name: "umbraco"
    path: "."

scanners:
  enabled:
    - nuget-dependencies
    - aspnetcore-rest
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
echo "Running DocArchitect on Umbraco..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/umbraco:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Umbraco scan complete."
echo "  Results: output/umbraco/"
echo ""
echo "Expected outputs:"
echo "  - 20+ components (CMS modules)"
echo "  - 80+ NuGet dependencies"
echo "  - 100+ ASP.NET Core API endpoints"
echo "  - 50+ EF Core entities"
echo ""
