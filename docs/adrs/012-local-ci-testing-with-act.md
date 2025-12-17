---
# Backstage TechDocs metadata
id: adr-012
title: ADR-012: Local CI Testing with Act
description: Use Act (nektos/act) to test GitHub Actions workflows locally before pushing to GitHub
tags:
  - adr
  - architecture
  - ci-cd
  - testing
  - github-actions
---
# ADR-012: Local CI Testing with Act

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2024-01-15 |
| **Deciders** | Development Team |
| **Technical Story** | Improve developer experience and reduce CI failures |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

Developers frequently push code to GitHub only to discover CI failures minutes later. This creates several problems:

- **Slow feedback loop**: Waiting 5-10 minutes for GitHub Actions to run
- **Polluted commit history**: "fix CI" commits cluttering the repository
- **Resource waste**: Unnecessary GitHub Actions minutes consumed
- **Developer frustration**: Context switching while waiting for CI results
- **Quality gate delays**: Build failures discovered late in the process

We need a way to test GitHub Actions workflows locally before pushing code, enabling developers to:

1. Verify builds pass before committing
2. Test quality gates (JaCoCo, Checkstyle, SpotBugs)
3. Validate Docker image builds
4. Debug workflow issues faster

---

## Decision

We will use **Act** (nektos/act) as the standard tool for local GitHub Actions testing.

**Implementation approach:**

1. **Install Act** on developer machines (via Chocolatey, Scoop, Homebrew, or direct download)
2. **Provide .actrc configuration** with optimized defaults (medium container, 4GB memory)
3. **Create testing guide** documenting common workflows and troubleshooting
4. **Standardize on container image**: `catthehacker/ubuntu:act-latest`
5. **Make it optional**: Act is recommended but not mandatory

---

## Rationale

Act was chosen because it:

- **Runs GitHub Actions locally** using Docker containers
- **Matches CI environment** closely (Ubuntu-based runners)
- **Fast feedback**: Results in seconds vs. minutes
- **Zero cost**: No GitHub Actions minutes consumed
- **Debuggable**: Can drop into shell for troubleshooting
- **Actively maintained**: 40k+ GitHub stars, frequent updates
- **Cross-platform**: Works on Windows, macOS, Linux

**Key benefits:**

✅ Catch failures before pushing (quality gates, test failures)
✅ Faster development cycle (instant feedback)
✅ Reduced CI costs (fewer GitHub Actions runs)
✅ Improved code quality (developers verify before committing)
✅ Better debugging (local shell access to failed workflows)

---

## Alternatives Considered

### Alternative 1: GitHub Actions Local Runner

**Description:** Use GitHub's official local runner

**Pros:**

- Official GitHub solution
- 100% compatibility with GitHub Actions

**Cons:**

- Requires self-hosted runner setup
- Complex configuration
- No offline support
- Still consumes GitHub Actions minutes

**Decision:** ❌ Rejected - Too complex, still requires GitHub connectivity

### Alternative 2: Docker Compose for Tests

**Description:** Manually replicate CI steps in Docker Compose

**Pros:**

- Full control over environment
- No third-party tools

**Cons:**

- Manual workflow replication (duplication)
- No GitHub Actions syntax support
- Maintenance burden when workflows change
- Doesn't test actual workflow files

**Decision:** ❌ Rejected - Too much maintenance overhead

### Alternative 3: Shell Scripts

**Description:** Create shell scripts mimicking CI steps

**Pros:**

- Simple and fast
- No dependencies

**Cons:**

- Doesn't test actual workflows
- Environment differs from CI
- Manual synchronization required
- Platform-specific (bash vs. cmd)

**Decision:** ❌ Rejected - Doesn't verify actual workflow files

---

## Consequences

### Positive

✅ Developers can test CI locally before pushing
✅ Faster feedback loop (seconds vs. minutes)
✅ Reduced "fix CI" commits
✅ Lower GitHub Actions costs
✅ Better developer experience
✅ Early detection of quality gate violations

### Negative

⚠️ Requires Docker Desktop installation (~2GB)
⚠️ Additional tool for developers to learn
⚠️ Act has slight differences from real GitHub Actions
⚠️ Initial setup time (~10 minutes per developer)
⚠️ Some workflow features may not work identically

### Neutral

🔵 Optional tool (developers can skip if preferred)
🔵 Adds ~500MB disk space for Act binary and containers
🔵 Requires periodic Act updates for compatibility

---

## Implementation Notes

### Installation

**Windows (Chocolatey):**

```bash
choco install act-cli
```

**macOS:**

```bash
brew install act
```

**Linux:**

```bash
curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
```

### Configuration (.actrc)

```bash
# filepath: c:\Users\Emhol\git\doc-architect\.actrc
-P ubuntu-latest=catthehacker/ubuntu:act-latest
--container-options="--cpus 2 --memory 4g"
-v
```

### Common Testing Commands

**Test PR workflow:**

```bash
act pull_request
```

**Test specific job:**

```bash
act pull_request -j build
```

**Test with secrets:**

```bash
act pull_request --secret-file .secrets
```

**Dry run (plan mode):**

```bash
act pull_request -n
```

**Debug with shell:**

```bash
act pull_request --shell
```

### Quality Gates Tested Locally

1. **JaCoCo Coverage**: ≥60% line coverage per package
2. **Checkstyle**: Google Java Style Guide compliance
3. **SpotBugs**: Static analysis (max effort, low threshold)
4. **Unit Tests**: All JUnit 5 tests must pass

### Testing Workflow

```bash
# 1. Before committing changes
act pull_request -j build

# 2. If build passes, test Docker image
act pull_request -j docker

# 3. Verify documentation generation
act pull_request -W .github/workflows/techdocs.yml

# 4. If all pass, commit and push
git commit -m "feature: add new capability"
git push
```

### Troubleshooting

**Slow first run:**

```bash
# Pre-download container image
docker pull catthehacker/ubuntu:act-latest
```

**Out of memory:**

```bash
# Increase memory in .actrc
--container-options="--cpus 4 --memory 8g"
```

**Docker permission denied (Linux):**

```bash
sudo usermod -aG docker $USER
newgrp docker
```

---

## Compliance

- **Architecture Principles**: Supports "Shift Left Testing" and "Fast Feedback"
- **Standards**: Follows GitHub Actions specification
- **Security**: Runs in isolated Docker containers, no credential sharing
- **Performance**: Minimal impact (only runs when invoked by developer)

---

## References

- [Act GitHub Repository](https://github.com/nektos/act)
- [Act Documentation](https://nektosact.com/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Desktop](https://www.docker.com/products/docker-desktop)
- [Local CI Testing Guide](../../LOCAL_CI_TESTING.md)

---

## Metadata

- **Review Date:** 2025-01-15
- **Last Updated:** 2024-01-15
- **Version:** 1.0
