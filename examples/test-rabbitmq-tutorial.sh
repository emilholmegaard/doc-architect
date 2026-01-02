#!/bin/bash
# test-rabbitmq-tutorial.sh
# Tests DocArchitect against RabbitMQ tutorials with real @RabbitListener usage

set -e

PROJECT_DIR="test-projects/rabbitmq-tutorials"

echo "=========================================="
echo "RabbitMQ Tutorial Test"
echo "Project: RabbitMQ Spring AMQP Tutorials"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating RabbitMQ tutorials..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning RabbitMQ tutorials..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/rabbitmq/rabbitmq-tutorials.git "$PROJECT_DIR"
fi

# Scan spring-amqp subdirectory
SCAN_DIR="$PROJECT_DIR/spring-amqp"

# Create DocArchitect config
cat > "$SCAN_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "RabbitMQ Spring AMQP Tutorials"
  version: "1.0.0"
  description: "RabbitMQ messaging patterns with Spring AMQP"

repositories:
  - name: "rabbitmq-tutorials"
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
echo "Running DocArchitect on RabbitMQ tutorials..."
mkdir -p "$(pwd)/output/rabbitmq-tutorials"
docker run --rm \
    -v "$(pwd)/$SCAN_DIR:/workspace:ro" \
    -v "$(pwd)/output/rabbitmq-tutorials:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ RabbitMQ tutorials scan complete."
echo "  Results: output/rabbitmq-tutorials/"
echo ""
echo "Expected outputs:"
echo "  - 6+ components (tutorial modules)"
echo "  - 5+ RabbitMQ message flows (queues and exchanges)"
echo "  - @RabbitListener annotations detected"
echo "  - Queue declarations"
echo ""
