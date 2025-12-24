#!/bin/bash
# test-go-linkerd2.sh
# Tests DocArchitect against Linkerd2 service mesh

set -e

PROJECT_DIR="test-projects/linkerd2"

echo "=========================================="
echo "Go Service Mesh Test"
echo "Project: Linkerd2"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Linkerd2..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Linkerd2..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/linkerd/linkerd2.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Linkerd2"
  version: "2.14"
  description: "Service Mesh for Kubernetes"

repositories:
  - name: "linkerd2"
    path: "."

scanners:
  enabled:
    - go-dependencies
    - protobuf-schema

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
echo "Running DocArchitect on Linkerd2..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/linkerd2:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Linkerd2 scan complete."
echo "  Results: output/linkerd2/"
echo ""
echo "Expected outputs:"
echo "  - 10+ microservices components"
echo "  - 50+ Go module dependencies"
echo "  - gRPC service definitions"
echo "  - Protobuf schemas"
echo ""
