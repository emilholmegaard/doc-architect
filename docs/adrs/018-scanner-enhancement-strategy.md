---
# Backstage TechDocs metadata
id: adr-018-scanner-enhancement-strategy
title: ADR-018: Scanner Enhancement Strategy for Complete Architecture Extraction
description: Comprehensive strategy to address critical gaps in scanner coverage based on real-world test suite analysis
tags:
  - adr
  - architecture
  - scanners
  - enhancement
---

# ADR-018: Scanner Enhancement Strategy for Complete Architecture Extraction

| Property | Value |
|----------|-------|
| **Status** | Proposed |
| **Date** | 2025-12-29 |
| **Deciders** | Development Team |
| **Technical Story** | Analysis of run-all-tests.sh output across 14 real-world projects |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

After running the comprehensive test suite (`run-all-tests.sh`) against 14 real-world projects spanning Java, .NET, Python, Go, and Ruby, we discovered critical gaps in our scanning capabilities:

### Critical Findings

**1. Message Flows: 100% Failure Rate**
- **ALL 14 projects** reported 0 message flows
- Despite having KafkaScanner and RabbitMQScanner implemented
- PiggyMetrics explicitly uses RabbitMQ but scanner found nothing
- Indicates either bugs in existing scanners or missing patterns

**2. Relationships: 78% Missing**
- Only 3 out of 14 projects (21%) found relationships
- Projects with relationships: Keycloak (71), FastAPI (1), Saleor (10)
- All relationships came from ORM scanners (JPA, SQLAlchemy, Django)
- **Zero service-to-service relationships** detected across all microservice projects

**3. Components: Severe Under-Extraction**
- Monoliths showing 0-1 components when expected 3-8 logical components
- Examples:
  - eShopOnWeb: 1 component (expected 4-5 projects)
  - Gitea: 1 component (expected 5-8 packages)
  - Saleor: 0 components (expected 15+ Django apps)
  - Umbraco: 1 component (expected 10+ modules)

**4. API Coverage Gaps by Technology**
- **Go**: 0 endpoints across Gitea (expected 100+), Mattermost (expected 200+)
- **Ruby**: GitLab found 70 GraphQL ops but 0 REST endpoints (expected 500+)
- **.NET**: Umbraco found 1 endpoint (expected 50+), eShopOnWeb found 6 (expected 15+)
- **Python**: Saleor GraphQL schema skipped (35k lines > 10k limit)

### Business Impact

Without complete architecture extraction, DocArchitect cannot:
- Generate accurate C4 diagrams for microservices
- Identify service-to-service dependencies
- Map message flows in event-driven architectures
- Detect architectural violations (circular dependencies, layer violations)
- Provide value for enterprise monoliths beyond dependency management

---

## Decision

We will implement a **phased enhancement strategy** addressing gaps in order of criticality:

### Phase 1: Fix Existing Scanners (P0)
1. **Debug message flow scanners** - Investigate why KafkaScanner/RabbitMQScanner return 0 results
2. **Enhance pre-filtering** - Improve file detection to reduce false negatives
3. **Add debug logging** - Enable troubleshooting for scanner failures

### Phase 2: Cross-Cutting Enhancements (P0-P1)
4. **Multi-level component extraction**
   - Level 1: Build files (current - Maven modules, NuGet projects)
   - Level 2: Framework components (Spring @Service, Django apps, Go packages)
   - Level 3: Logical grouping (package namespaces, directory structure)
5. **Service relationship scanner** - HTTP client call detection (RestTemplate, HttpClient, requests, etc.)
6. **Database relationship scanner** - Service → Database (READS_FROM, WRITES_TO)

### Phase 3: Language-Specific Scanners (P1-P2)
7. **Go HTTP Router Scanner** - Gin, Echo, Chi, Gorilla, net/http patterns
8. **Go Struct Scanner** - XORM/GORM model detection
9. **Rails Route Scanner** - Parse routes.rb or rails routes output
10. **Rails ActiveRecord Enhancement** - Full association mapping
11. **MongoDB Scanner (Java)** - Spring Data MongoDB @Document detection
12. **Enhanced Entity Framework** - DbContext/DbSet analysis
13. **Enhanced ASP.NET Core API** - MinimalAPI, MVC, Razor Pages
14. **Django App Component Scanner** - INSTALLED_APPS and app structure
15. **Python Dependency Scanner Fix** - Saleor showed 0 dependencies

### Phase 4: Messaging & Background Jobs (P2)
16. **Celery Task Scanner** - Python @task decorator detection
17. **Sidekiq Job Scanner** - Ruby Sidekiq::Worker detection
18. **.NET Message Queue Scanner** - MassTransit, Azure Service Bus, RabbitMQ.NET

### Phase 5: Quality Improvements (P2-P3)
19. **Flask Route Scanner** - @app.route detection
20. **gRPC Service Scanner (Go)** - Link Protobuf to Go implementations
21. **GraphQL Schema Size Limit** - Increase from 10k to 50k lines
22. **AST Parser Performance** - Parallel parsing, caching

---

## Rationale

### Why This Approach?

**1. Fix Before Extend**
- Existing scanners returning 0 results indicates bugs, not missing features
- Debugging message flow scanners is lower effort than building new ones
- Proves the architecture works before expanding

**2. Cross-Cutting Before Language-Specific**
- Component and relationship extraction benefits ALL technologies
- Service-to-service relationships are critical for microservices
- Higher ROI than individual language scanners

**3. Data-Driven Prioritization**
- Based on real-world test results, not assumptions
- Addresses gaps in actual enterprise projects (Keycloak, GitLab, etc.)
- Focuses on missing capabilities, not nice-to-haves

**4. Incremental Value Delivery**
- Each phase delivers measurable improvements
- Can validate strategy before full commitment
- Allows course correction based on feedback

---

## Alternatives Considered

### Alternative 1: Build All Missing Scanners First

**Description:** Implement all 25+ missing scanners identified in gap analysis

**Pros:**
- Comprehensive coverage immediately
- Parallelizable work

**Cons:**
- Doesn't address bugs in existing scanners
- High effort before any value delivery
- May build scanners for rarely-used features

**Decision:** ❌ Rejected - Violates "fix before extend" principle

### Alternative 2: Focus Only on Message Flows

**Description:** Solve the 100% failure rate in message flow detection

**Pros:**
- Addresses most critical gap
- Clear success metric (0 → N message flows)

**Cons:**
- Ignores component and relationship gaps
- Doesn't help Go/Ruby projects with no message flow scanners

**Decision:** ❌ Rejected - Too narrow, misses cross-cutting improvements

### Alternative 3: Language-by-Language Completion

**Description:** Complete all scanners for one language before moving to next

**Pros:**
- Each language becomes "fully supported"
- Marketing value ("complete Go support")

**Cons:**
- Lower ROI - some languages used in 1-2 test projects
- Delays cross-cutting features that benefit all languages

**Decision:** ❌ Rejected - Doesn't prioritize by impact

---

## Consequences

### Positive

✅ **Measurable Progress** - Can track completion against test suite expectations
✅ **Real-World Validation** - Every enhancement tested against actual projects
✅ **Risk Reduction** - Phased approach allows early detection of architecture issues
✅ **Comprehensive Coverage** - Addresses gaps across all technologies
✅ **Prioritized by Impact** - Focus on critical gaps (P0) before nice-to-haves (P3)

### Negative

⚠️ **Long Timeline** - 25+ enhancements across 5 phases
⚠️ **Ongoing Maintenance** - More scanners = more code to maintain
⚠️ **Potential Complexity** - Multi-level component extraction may be confusing
⚠️ **AST Parser Dependencies** - Some languages may need new parser libraries

### Neutral

🔵 **Test Suite Becomes Contract** - Expected outputs define "done" for each scanner
🔵 **ADR-016 Dependency** - Pre-filtering strategy must be applied consistently
🔵 **Configuration Complexity** - May need scanner-specific settings (ADR-017)

---

## Implementation Notes

### Phase 1: Debugging Message Flow Scanners

**KafkaScanner Investigation** (console line 28):
```
07:01:04.249 [main] INFO  c.d.c.s.impl.java.RabbitMQScanner - Scanning RabbitMQ message flows in: /workspace
07:01:05.706 [main] INFO  c.d.c.s.impl.java.RabbitMQScanner - Found 0 RabbitMQ message flows
```

**Actions:**
1. Add debug logging for file pre-filtering
2. Log which files are scanned vs. skipped
3. Check if import detection is too strict
4. Verify annotation patterns match actual usage

**Example Debug Output:**
```
DEBUG c.d.c.s.impl.java.RabbitMQScanner - Pre-filter: src/main/java/Foo.java
DEBUG c.d.c.s.impl.java.RabbitMQScanner -   ✅ Contains 'org.springframework.amqp'
DEBUG c.d.c.s.impl.java.RabbitMQScanner -   Parsing file...
DEBUG c.d.c.s.impl.java.RabbitMQScanner -   Found @RabbitListener in method Foo.handleMessage
DEBUG c.d.c.s.impl.java.RabbitMQScanner -   Extracted queue: notification-queue
```

### Phase 2: Multi-Level Component Extraction

**Configuration Example:**
```yaml
components:
  extraction:
    strategy: hybrid  # build-file | framework | package | hybrid
    granularity: module  # file | class | module | project

    # Build-file components (Level 1)
    buildFiles:
      enabled: true

    # Framework components (Level 2)
    framework:
      enabled: true
      java:
        - "@Service"
        - "@Component"
        - "@RestController"
      python:
        - "Django apps from INSTALLED_APPS"
      go:
        - "package main"

    # Logical grouping (Level 3)
    grouping:
      enabled: true
      patterns:
        - pattern: "com.example.auth.**"
          name: "Authentication Service"
          type: SERVICE
        - pattern: "com.example.billing.**"
          name: "Billing Service"
          type: SERVICE
```

**Implementation:**
1. Create `ComponentExtractionStrategy` interface
2. Implement: `BuildFileStrategy`, `FrameworkStrategy`, `PackageGroupingStrategy`
3. Combine strategies in `HybridComponentExtractor`
4. Return merged component list

### Service Relationship Scanner

**Patterns to Detect:**

**Java:**
```java
@FeignClient(name = "user-service")
interface UserClient {
    @GetMapping("/api/users/{id}")
    User getUser(@PathVariable Long id);
}
// → Create Relationship: current-service CALLS user-service via /api/users/{id}

@Autowired RestTemplate restTemplate;
restTemplate.getForObject("http://order-service/api/orders", ...)
// → Create Relationship: current-service CALLS order-service
```

**C#:**
```csharp
private readonly HttpClient _client;
var response = await _client.GetAsync("http://catalog-api/products");
// → Create Relationship: current-service CALLS catalog-api
```

**Python:**
```python
import requests
response = requests.get("http://auth-service/api/login")
# → Create Relationship: current-service CALLS auth-service
```

**Go:**
```go
resp, err := http.Get("http://users-api/v1/users")
// → Create Relationship: current-service CALLS users-api
```

### Testing Strategy

**Test Updates Required:**

`test-spring-microservices.sh`:
```bash
Expected outputs:
  - 9 components (one per Maven module) ✅
  - 24+ dependencies ✅
  - 20+ REST endpoints (currently 11)  # Need enhancement
  - 10+ MongoDB entities              # Need MongoDB scanner
  - 5+ message flows (RabbitMQ)       # Need debug/fix
  - 15+ service relationships          # Need HTTP client scanner
```

**Success Criteria:**
- Each phase must improve at least 50% of failing test expectations
- No regression in existing passing tests
- Coverage remains ≥80% for new code

---

## Compliance

### Architecture Principles

**Plugin Architecture (ADR-001):**
- All new scanners implement `Scanner` SPI
- Registered via `META-INF/services`
- Independently testable

**AST-First Parsing (ADR-011):**
- HTTP client scanners use AST parsing (JavaParser, Python AST, etc.)
- Framework component scanners leverage existing AST parsers
- Avoid regex for complex patterns

**Pre-Filtering (ADR-016):**
- All scanners implement `shouldScanFile()`
- Import-based detection before AST parsing
- Debug logging for pre-filter decisions

**Configuration-Driven (ADR-017):**
- Component extraction strategy configurable
- Scanner-specific settings in YAML
- Opt-in for expensive scanners

### Standards

- **SPI Pattern:** All scanners follow `AbstractScanner` base class patterns
- **Testing:** Unit tests + integration tests in real-world suite
- **Logging:** SLF4J with configurable levels per scanner (ADR-005)
- **Javadoc:** Complete documentation for all public APIs

### Security

- **No Code Execution:** Scanners only parse, never execute code
- **File System Boundaries:** Respect read-only mounts in Docker
- **Input Validation:** Sanitize extracted file paths and URLs

### Performance

**Current Performance (from console):**
- Keycloak: Parsed 1876/7279 Java files (25%) in ~90 seconds
- GitLab: Scanned 33 Gemfiles in ~165 seconds (Ruby gems expensive)
- eShopOnWeb: Parsed 38/254 C# files in ~8 seconds

**Performance Goals:**
- Pre-filtering should eliminate ≥70% of files
- Parallel parsing for independent scanners
- Scanner execution <5 minutes for projects <10k files

---

## References

- Console Output Analysis: `console-out.txt` (1249 lines from 14 projects)
- Test Suite: `examples/run-all-tests.sh`
- Scanner Architecture: [doc-architect-core/src/main/java/com/docarchitect/core/scanner](../../doc-architect-core/src/main/java/com/docarchitect/core/scanner)
- ADR-011: AST-First Parsing Strategy
- ADR-015: Real-World Scanner Validation Findings
- ADR-016: Import-Based Scanner Pre-Filtering
- ADR-017: Configuration-Driven Scanner Execution

---

## Metadata

- **Review Date:** 2025-03-29 (3 months - after Phase 1-2 completion)
- **Last Updated:** 2025-12-29
- **Version:** 1.0
