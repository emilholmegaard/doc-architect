#!/bin/bash
# test-go-gitea.sh
# Tests DocArchitect against Gitea Git service written in Go

set -e

PROJECT_DIR="test-projects/gitea"

echo "=========================================="
echo "Go Git Service Test"
echo "Project: Gitea"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Gitea..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Gitea..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/go-gitea/gitea.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Gitea"
  version: "1.21"
  description: "Git Service Written in Go"

repositories:
  - name: "gitea"
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
echo "Running DocArchitect on Gitea..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/gitea"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/gitea:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Gitea scan complete."
echo "  Results: output/gitea/"
echo ""
echo "Expected outputs:"
echo "  - 1 main component (monolith)"
echo "  - 100+ Go module dependencies"
echo "  - 80+ REST API endpoints (Gin framework)"
echo "  - Database models"
echo ""
