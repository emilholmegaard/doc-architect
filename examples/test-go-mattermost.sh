#!/bin/bash
# test-go-mattermost.sh
# Tests DocArchitect against Mattermost team collaboration platform

set -e

PROJECT_DIR="test-projects/mattermost"

echo "=========================================="
echo "Go/React Collaboration Platform Test"
echo "Project: Mattermost"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Mattermost..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Mattermost..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/mattermost/mattermost.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Mattermost"
  version: "9.0"
  description: "Team Collaboration Platform with Go Backend"

repositories:
  - name: "mattermost"
    path: "server"

scanners:
  enabled:
    - go-modules

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
echo "Running DocArchitect on Mattermost..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/mattermost:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Mattermost scan complete."
echo "  Results: output/mattermost/"
echo ""
echo "Expected outputs:"
echo "  - 10+ components (Go services)"
echo "  - 100+ Go module dependencies"
echo "  - 100+ REST API endpoints"
echo "  - WebSocket communication patterns"
echo ""
