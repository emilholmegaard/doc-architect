#!/bin/bash
# test-kafka-streams-java.sh
# Tests DocArchitect against Confluent Kafka Streams examples (Java)
#
# Repository: https://github.com/confluentinc/kafka-streams-examples
# Contains: Word count, page views, aggregations, joins, and interactive queries

set -e

PROJECT_DIR="test-projects/kafka-streams-examples"

echo "=========================================="
echo "Kafka Streams Java Examples Test"
echo "Project: Confluent Kafka Streams Examples"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Kafka Streams examples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Kafka Streams examples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/confluentinc/kafka-streams-examples.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Confluent Kafka Streams Examples"
  version: "1.0.0"
  description: "Real-time stream processing with Kafka Streams API"

repositories:
  - name: "kafka-streams-examples"
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
echo "Running DocArchitect on Kafka Streams Java examples..."
mkdir -p "$(pwd)/output/kafka-streams-java"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/kafka-streams-java:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Kafka Streams Java examples scan complete."
echo "  Results: output/kafka-streams-java/"
echo ""
echo "Expected outputs:"
echo "  - 10+ components (stream processing applications)"
echo "  - 20+ Maven dependencies (Kafka Streams, Avro, etc.)"
echo "  - 15+ Kafka message flows (topics, streams, KTables)"
echo "  - Stream topologies (WordCount, PageViewRegion, etc.)"
echo ""
echo "Notable examples:"
echo "  - WordCountLambdaExample - Classic word count with DSL"
echo "  - PageViewRegionExample - Joins between streams"
echo "  - MapFunctionLambdaExample - Stream transformations"
echo "  - TopArticlesLambdaExample - Aggregations and windowing"
echo ""
