---
title: Message Flow Testing Improvements
status: Implemented
date: 2025-12-29
related_issue: 178
---

# Message Flow Testing Improvements

## Overview

This document describes improvements to message flow scanner testing based on the findings in [Issue #178](https://github.com/emilholmegaard/doc-architect/issues/178).

## Problem Statement

Message flow scanners (Kafka, RabbitMQ) were returning 0 results across all 14 test projects. Analysis revealed this was due to **test project selection**, not scanner bugs:

- **PiggyMetrics**: Uses RabbitMQ only for infrastructure (Spring Cloud Bus), not business messaging
- **Most test projects**: Do not use message queues for business logic
- **Expected patterns missing**: Test projects lacked `@RabbitListener`, `@KafkaListener`, etc.

## Solution

### 1. Minimal Validation Test Case

Created [test-messaging-validation.sh](../../examples/test-messaging-validation.sh) - a minimal Spring Boot application with:

- **RabbitMQ Listeners**: `@RabbitListener` annotations on queues
- **RabbitMQ Producers**: `RabbitTemplate.convertAndSend()` calls
- **Kafka Listeners**: `@KafkaListener` annotations on topics
- **Kafka Producers**: `KafkaTemplate.send()` calls

**Purpose**: Prove that scanners work correctly with clear, unambiguous messaging code.

**Expected Results**:
- Minimum 4 message flows detected
- Validates both Kafka and RabbitMQ scanners
- If this test fails, there's a scanner bug

### 2. Real-World Messaging Projects

Test files already exist for projects with actual message flows:

#### Kafka Spring Cloud Stream
- **File**: [test-kafka-spring-cloud-stream.sh](../../examples/test-kafka-spring-cloud-stream.sh)
- **Project**: https://github.com/spring-cloud/spring-cloud-stream-samples
- **Expected**: 8+ Kafka message flows
- **Contains**: Stream processors, Kafka topics, event-driven patterns

#### RabbitMQ Spring AMQP Tutorials
- **File**: [test-rabbitmq-tutorial.sh](../../examples/test-rabbitmq-tutorial.sh)
- **Project**: https://github.com/rabbitmq/rabbitmq-tutorials
- **Expected**: 5+ RabbitMQ message flows
- **Contains**: Queue declarations, `@RabbitListener`, exchange bindings

#### Eventuate Tram Sagas
- **File**: [test-eventuate-tram.sh](../../examples/test-eventuate-tram.sh)
- **Project**: https://github.com/eventuate-tram/eventuate-tram-sagas-examples-customers-and-orders
- **Expected**: 10+ Kafka message flows (domain events)
- **Contains**: Saga orchestration, event sourcing, microservice events

### 3. Updated Test Expectations

#### PiggyMetrics (Already Correct)
- **File**: [test-spring-microservices.sh](../../examples/test-spring-microservices.sh)
- **Expected**: 0 message flows ✅
- **Note**: Uses RabbitMQ only for Spring Cloud Bus (infrastructure)
- **Business communication**: REST APIs via Feign, not async messaging

### 4. Enhanced Validation Script

Updated [validate-outputs.sh](../../examples/validate-outputs.sh) with:

#### New Validation Functions
- `validate_messaging_validation()` - Validates minimal test case (CRITICAL)
- `validate_kafka_stream_samples()` - Validates Kafka samples
- `validate_rabbitmq_tutorials()` - Validates RabbitMQ tutorials
- `validate_eventuate_tram()` - Validates event-driven sagas

#### Critical Failure Detection
- Detects when message flow count = 0 (scanner likely broken)
- Flags as CRITICAL_FAILURES for visibility
- Distinguishes between "no messaging in project" vs "scanner bug"

#### Two-Phase Validation
1. **Part 1**: Messaging scanner validation (critical tests)
2. **Part 2**: General project validation (REST, dependencies, etc.)

### 5. Updated Test Runner

Modified [run-all-tests.sh](../../examples/run-all-tests.sh):

- **First**: Run minimal validation test (MUST pass)
- **Then**: Run messaging-specific projects
- **Finally**: Run general tests

**Output hierarchy**:
```
⭐⭐⭐ Messaging Validation (Minimal) - CRITICAL
⭐ Kafka Spring Cloud Stream
⭐ RabbitMQ Spring AMQP Tutorials
⭐ Eventuate Tram Sagas
```

## Implementation Details

### Test File Structure

All test files follow this pattern:
1. Clone/update test project
2. Create `docarchitect.yaml` configuration
3. Run DocArchitect scanner via Docker
4. Validate expected outputs

### Validation Criteria

Each messaging test validates:
- **Minimum component count**: Ensures project scanned
- **Minimum dependency count**: Ensures dependency scanner works
- **Minimum message flow count**: **CRITICAL** - proves messaging scanners work
- **Specific content**: Validates actual queue/topic names appear

### Critical Failure Thresholds

| Test Project | Min Message Flows | Failure Severity |
|--------------|-------------------|------------------|
| Messaging Validation | 4 | CRITICAL |
| Kafka Spring Cloud Stream | 8 | CRITICAL |
| RabbitMQ Tutorials | 5 | CRITICAL |
| Eventuate Tram | 10 | CRITICAL |
| PiggyMetrics | 0 | Expected ✅ |

## Test Execution

### Run All Tests
```bash
bash examples/run-all-tests.sh
```

### Run Only Messaging Validation
```bash
bash examples/test-messaging-validation.sh
```

### Run Validation Script
```bash
bash examples/validate-outputs.sh
```

## Expected Outcomes

### If Minimal Test Passes
✅ **Scanners ARE working correctly**
- Issue is test project selection, not scanner bugs
- Focus on adding more real-world messaging projects

### If Minimal Test Fails
❌ **Scanner bug confirmed**
- Investigate KafkaScanner or RabbitMQScanner implementation
- Check pre-filtering logic
- Review JavaParser usage

## Future Work

### Additional Scanner Types (from Issue #178)
- [ ] Spring Cloud Bus Scanner - detect infrastructure messaging (Hystrix, Turbine)
- [ ] Celery Scanner - Python background jobs ([#163](https://github.com/emilholmegaard/doc-architect/issues/163))
- [ ] Sidekiq Scanner - Ruby background jobs ([#164](https://github.com/emilholmegaard/doc-architect/issues/164))
- [ ] REST-based Event Flow Scanner - REST endpoints used for events

### Additional Test Projects
- [ ] Kafka Streams Examples
- [ ] Azure Service Bus (eShopOnContainers - currently failing)
- [ ] Spring Integration samples
- [ ] Apache Camel routes

## Related Documentation

- [Issue #178](https://github.com/emilholmegaard/doc-architect/issues/178) - Message flow scanners finding 0 results
- [KafkaScanner](../../doc-architect-core/src/main/java/com/docarchitect/core/scanner/impl/java/KafkaScanner.java)
- [RabbitMQScanner](../../doc-architect-core/src/main/java/com/docarchitect/core/scanner/impl/java/RabbitMQScanner.java)

## Acceptance Criteria

✅ Minimal validation test case created
✅ Test files for Kafka/RabbitMQ/Eventuate projects verified
✅ PiggyMetrics expectations updated (0 message flows)
✅ Validation script enhanced with message flow checks
✅ Test runner includes messaging tests first
✅ Documentation updated

## Spring Cloud Bus / Hystrix Considerations

**Question**: Should we implement a Spring Cloud Bus scanner for Hystrix?

**Analysis**:
- Hystrix is a **circuit breaker** pattern, not message queuing
- Spring Cloud Bus uses RabbitMQ/Kafka for **infrastructure** (config refresh, metrics)
- This is **opaque to application code** - no `@RabbitListener` in business logic
- **Not detectable** via source code scanning (configured via dependencies only)

**Recommendation**:
- ❌ **Do not implement** Spring Cloud Bus scanner
- ✅ **Focus on** business-level message flows (queues, topics, events)
- ✅ **Document** that infrastructure messaging (Hystrix metrics, Turbine) is out of scope

**Rationale**:
- DocArchitect scans **source code**, not runtime behavior
- Infrastructure messaging has no source-level artifacts
- Business value is in documenting **application-level** message flows
- Hystrix is deprecated (replaced by Resilience4j) - low ROI

## Summary

These improvements ensure that:
1. **Scanners are validated** with minimal test cases
2. **Real projects** with actual messaging are tested
3. **Test expectations** match reality (PiggyMetrics = 0 flows is correct)
4. **Critical failures** are clearly identified
5. **Future work** is documented for additional scanner types
