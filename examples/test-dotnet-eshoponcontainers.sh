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
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating eShopOnContainers..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning eShopOnContainers..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/dotnet-architecture/eShopOnContainers.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "eShopOnContainers"
  version: "6.0"
  description: ".NET Microservices Reference Architecture"

repositories:
  - name: "eshoponcontainers"
    path: "."

scanners:
  enabled:
    - nuget-dependencies
    - dotnet-solution
    - aspnetcore-rest
    - entity-framework
    - rabbitmq-messaging  # eShopOnContainers uses RabbitMQ for event bus

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
echo "✓ eShopOnContainers scan complete."
echo "  Results: output/eshoponcontainers/"
echo ""
echo "Expected outputs:"
echo "  - 10+ microservices components"
echo "  - 50+ REST API endpoints"
echo "  - 30+ Entity Framework entities"
echo "  - gRPC service definitions"
echo "  - Inter-service communication patterns"
echo ""
