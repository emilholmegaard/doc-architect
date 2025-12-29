---
title: Advanced Messaging Framework Test Examples
status: Implemented
date: 2025-12-29
related_issue: 178
---

# Advanced Messaging Framework Test Examples

## Overview

This document describes the additional messaging framework test examples added to complement the initial message flow testing improvements from [Issue #178](https://github.com/emilholmegaard/doc-architect/issues/178).

## Requested Frameworks

Based on user request, we've added test examples for:

1. **Kafka Streams** (Java, .NET, Python)
2. **Azure Service Bus** (eShopOnContainers)
3. **Spring Integration** (Enterprise Integration Patterns)
4. **Apache Camel** (Route-based integration)

## Test Files Created

### 1. Kafka Streams - Java

**File**: [test-kafka-streams-java.sh](../../examples/test-kafka-streams-java.sh)

**Project**: [confluentinc/kafka-streams-examples](https://github.com/confluentinc/kafka-streams-examples)

**Description**: Official Confluent Kafka Streams examples demonstrating real-time stream processing with the Kafka Streams API.

**Contains**:
- WordCountLambdaExample - Classic word count with DSL
- PageViewRegionExample - Joins between streams
- MapFunctionLambdaExample - Stream transformations
- TopArticlesLambdaExample - Aggregations and windowing

**Expected Results**:
- 10+ components (stream processing applications)
- 20+ Maven dependencies (Kafka Streams, Avro, etc.)
- 15+ Kafka message flows (topics, streams, KTables)
- Stream topologies and transformations

### 2. Kafka Streams - .NET

**File**: [test-kafka-streams-dotnet.sh](../../examples/test-kafka-streams-dotnet.sh)

**Project**: [confluentinc/confluent-kafka-dotnet](https://github.com/confluentinc/confluent-kafka-dotnet)

**Description**: Confluent's official .NET client for Apache Kafka with producer, consumer, and stream processing examples.

**Contains**:
- Consumer - Message consumption with offset management
- Producer - Async message production
- ExactlyOnce - Word count with exactly-once semantics
- AdminClient - Topic and configuration management

**Expected Results**:
- 5+ components (example applications)
- 10+ NuGet dependencies (Confluent.Kafka, etc.)
- 8+ Kafka message flows (producers and consumers)
- Transaction support and exactly-once semantics

### 3. Kafka Streams - Python (Faust)

**File**: [test-kafka-streams-python.sh](../../examples/test-kafka-streams-python.sh)

**Project**: [faust-streaming/faust](https://github.com/faust-streaming/faust)

**Description**: Stream processing library porting Kafka Streams concepts to Python.

**Key Concepts**:
- **Agents** = Stream processors
- **Topics** = Kafka topics
- **Tables** = KTables (changelog-backed state)
- **Records** = Data models

**Contains**:
- @app.agent decorators for stream processors
- Faust Records for data models
- Tables for stateful processing
- Async/await stream processing

**Expected Results**:
- 3+ components (stream processing applications)
- 15+ Python dependencies (faust-streaming, kafka-python, etc.)
- 6+ Kafka message flows (agents, topics, tables)
- Stream processing patterns (aggregations, joins, windowing)

### 4. Azure Service Bus / RabbitMQ - eShopOnContainers

**File**: [test-dotnet-eshoponcontainers.sh](../../examples/test-dotnet-eshoponcontainers.sh) (updated)

**Project**: [dotnet-architecture/eShopOnContainers](https://github.com/dotnet-architecture/eShopOnContainers)

**Description**: Microsoft's .NET microservices reference architecture with event bus implementation.

**Event Bus Implementation**:
- **Development**: RabbitMQ (EventBusRabbitMQ.cs)
- **Production**: Azure Service Bus (optional, via configuration)
- **Pattern**: Publish-Subscribe for loosely coupled microservices

**Integration Events**:
- OrderStatusChangedEvent
- UserCheckoutAcceptedEvent
- Product price changes
- Inventory updates

**Expected Results**:
- 10+ microservices components (Catalog, Basket, Ordering, Identity, etc.)
- 50+ REST API endpoints
- 30+ Entity Framework entities
- **15+ message flows** (event-driven integration)
- gRPC service definitions

**Note**: eShopOnContainers supports both RabbitMQ and Azure Service Bus as message brokers, switchable via configuration.

### 5. Spring Integration - Enterprise Integration Patterns

**File**: [test-spring-integration.sh](../../examples/test-spring-integration.sh)

**Project**: [spring-projects/spring-integration-samples](https://github.com/spring-projects/spring-integration-samples)

**Description**: Official Spring Integration samples demonstrating Enterprise Integration Patterns (EIP).

**EIP Patterns Implemented**:
- **Message Channel** - Point-to-point and pub-sub channels
- **Message Router** - Content-based routing
- **Message Translator** - Data transformation
- **Message Filter** - Selective message processing
- **Splitter/Aggregator** - Message decomposition and recomposition
- **Service Activator** - Message endpoint that invokes service

**Integration Adapters**:
- AMQP (RabbitMQ) - examples/basic/amqp/
- JMS - examples/basic/jms/
- File - examples/basic/file/
- HTTP - examples/basic/http/
- JDBC - examples/basic/jdbc/
- MongoDB - examples/basic/mongodb/

**Expected Results**:
- 30+ components (sample applications and modules)
- 50+ Maven dependencies (Spring Integration modules)
- 10+ message flows (JMS, AMQP, Kafka, File, HTTP)
- Comprehensive EIP pattern implementations

### 6. Apache Camel - Route-Based Integration

**File**: [test-apache-camel.sh](../../examples/test-apache-camel.sh)

**Project**: [apache/camel-examples](https://github.com/apache/camel-examples)

**Description**: Official Apache Camel examples demonstrating route-based integration with Enterprise Integration Patterns.

**EIP Patterns Implemented**:
- **Content-Based Router** - Route messages based on content
- **Message Filter** - Filter messages based on criteria
- **Splitter** - Split composite messages
- **Aggregator** - Combine related messages
- **Recipient List** - Send message to dynamic list of recipients
- **Wire Tap** - Route copy of message to secondary destination

**Integration Components**:
- **Kafka** - camel-kafka examples
- **RabbitMQ** - camel-rabbitmq examples
- **JMS/ActiveMQ** - camel-jms examples
- **REST** - camel-rest examples
- **Database** - camel-jdbc, camel-jpa examples
- **File** - camel-file examples

**Camel DSL**:
- **Java DSL** - Route builders in Java
- **XML DSL** - Route definitions in XML
- **YAML DSL** - Route definitions in YAML

**Expected Results**:
- 50+ components (example routes and applications)
- 100+ Maven dependencies (Camel components)
- 20+ message flows (Kafka, AMQP, JMS, ActiveMQ)
- EIP route definitions

## Validation Criteria

Each messaging framework test has specific validation thresholds:

| Framework | Min Components | Min Dependencies | Min Message Flows | Validation Function |
|-----------|---------------|------------------|-------------------|---------------------|
| Kafka Streams Java | 10 | 20 | 15 | `validate_kafka_streams_java()` |
| Kafka Streams .NET | 5 | 10 | 8 | `validate_kafka_streams_dotnet()` |
| Kafka Streams Python | 3 | 15 | 6 | `validate_kafka_streams_python()` |
| Spring Integration | 30 | 50 | 10 | `validate_spring_integration()` |
| Apache Camel | 50 | 100 | 20 | `validate_apache_camel()` |

## Test Execution

### Run All Tests
```bash
bash examples/run-all-tests.sh
```

### Run Individual Framework Tests
```bash
# Kafka Streams
bash examples/test-kafka-streams-java.sh
bash examples/test-kafka-streams-dotnet.sh
bash examples/test-kafka-streams-python.sh

# Integration Frameworks
bash examples/test-spring-integration.sh
bash examples/test-apache-camel.sh

# Azure Service Bus / RabbitMQ
bash examples/test-dotnet-eshoponcontainers.sh
```

### Validate Outputs
```bash
bash examples/validate-outputs.sh
```

## Integration with Existing Tests

The advanced messaging framework tests are integrated into the test suite in three phases:

**Phase 1**: Messaging Validation (CRITICAL)
- Minimal validation test
- Kafka Spring Cloud Stream
- RabbitMQ tutorials
- Eventuate Tram sagas

**Phase 2**: Advanced Messaging Frameworks (NEW)
- Kafka Streams (Java, .NET, Python)
- Spring Integration
- Apache Camel

**Phase 3**: General Project Validation
- All other test projects (PiggyMetrics, eShopOnWeb, etc.)

## Why These Frameworks?

### Kafka Streams
- **Industry standard** for stream processing
- **Multi-language** support demonstrates scanner flexibility
- **Complex patterns** (joins, aggregations, windowing)

### Azure Service Bus / RabbitMQ
- **Enterprise messaging** patterns
- **Cloud-native** (Azure) vs on-premises (RabbitMQ)
- **Microservices** event-driven architecture

### Spring Integration
- **EIP reference implementation** for Spring ecosystem
- **Comprehensive adapter library** (JMS, AMQP, File, HTTP, DB)
- **Message-driven architecture** patterns

### Apache Camel
- **Route-based integration** framework
- **300+ components** for system integration
- **DSL flexibility** (Java, XML, YAML)
- **Alternative to Spring Integration** in EIP space

## Scanner Requirements

These tests validate the following scanner capabilities:

### Java Scanners
- `KafkaScanner` - Detects @KafkaListener, KafkaTemplate
- `RabbitMQScanner` - Detects @RabbitListener, RabbitTemplate
- `MavenDependencyScanner` - Kafka, Spring Integration, Camel dependencies
- `SpringComponentScanner` - Spring Integration components
- `JavaParserScanner` - Camel route builders

### .NET Scanners
- `DotNetKafkaScanner` - Confluent.Kafka producer/consumer patterns (to be implemented)
- `NuGetDependencyScanner` - Confluent.Kafka, Azure Service Bus packages
- `AspNetCoreScanner` - eShopOnContainers REST APIs
- `EntityFrameworkScanner` - Domain entities

### Python Scanners
- `PythonKafkaScanner` - Faust @app.agent decorators (to be implemented)
- `PipPoetryScanner` - faust-streaming, kafka-python dependencies

## Future Enhancements

### Additional Scanners Needed
- [ ] **DotNetKafkaScanner** - Detect Confluent.Kafka patterns in C#
- [ ] **PythonFaustScanner** - Detect Faust stream processing patterns
- [ ] **AzureServiceBusScanner** - Detect Azure.Messaging.ServiceBus usage
- [ ] **CamelRouteScanner** - Parse Camel Java DSL routes
- [ ] **SpringIntegrationScanner** - Detect Spring Integration DSL patterns

### Test Enhancements
- [ ] Add validation for specific route patterns in Camel
- [ ] Validate EIP pattern implementations in Spring Integration
- [ ] Check for Kafka Streams topology descriptions
- [ ] Verify Azure Service Bus queue/topic declarations

## References

### Research Sources

**Kafka Streams Java**:
- [Confluent Kafka Streams Examples](https://github.com/confluentinc/kafka-streams-examples)
- [Apache Kafka Streams Documentation](https://github.com/apache/kafka)

**Kafka Streams .NET**:
- [Confluent Kafka .NET Client](https://github.com/confluentinc/confluent-kafka-dotnet)
- [DotNet Stream Processing](https://github.com/confluentinc/DotNetStreamProcessing)

**Kafka Streams Python**:
- [Faust Stream Processing](https://github.com/faust-streaming/faust)
- [Faust Documentation](https://faust.readthedocs.io/en/latest/)

**Spring Integration**:
- [Spring Integration Samples](https://github.com/spring-projects/spring-integration-samples)
- [Spring Integration Documentation](https://docs.spring.io/spring-integration/reference/samples.html)

**Apache Camel**:
- [Apache Camel Examples](https://github.com/apache/camel-examples)
- [Apache Camel Documentation](https://camel.apache.org/manual/examples.html)

**eShopOnContainers**:
- [eShopOnContainers Repository](https://github.com/dotnet-architecture/eShopOnContainers)
- [Event Bus Implementation](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/multi-container-microservice-net-applications/rabbitmq-event-bus-development-test-environment)

## Summary

These advanced messaging framework tests:
- ✅ Cover **5 major messaging/integration frameworks**
- ✅ Span **3 programming languages** (Java, .NET, Python)
- ✅ Demonstrate **stream processing** patterns
- ✅ Validate **Enterprise Integration Patterns** (EIP)
- ✅ Include **cloud-native** messaging (Azure Service Bus)
- ✅ Provide **comprehensive message flow coverage**

This ensures DocArchitect's message flow scanners are thoroughly tested across the major messaging and integration frameworks used in enterprise applications.
