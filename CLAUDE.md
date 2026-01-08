## Build Commands

```bash
# Build with tests
./gradlew build

# Build without tests
./gradlew assemble

# Run all tests for a module
./gradlew :agent:test

# Run a single test
./gradlew :agent:test --tests "com.teamscale.jacoco.agent.MyTest.testMethod"

# Publish to local Maven repo (for local plugin testing)
./gradlew publishToMavenLocal

# Build Docker image
docker build -f agent/src/docker/Dockerfile .

# Compile for different JaCoCo version (don't commit unless upgrading)
./gradlew dist
```

## Project Architecture

This is a multi-module Gradle project (Java 21 toolchain, targets Java 8) that provides a JVM profiler for collecting code coverage and uploading it to Teamscale.

### Core Modules

- **agent** - Main Java agent that instruments bytecode via JaCoCo and collects coverage
- **report-generator** - Converts JaCoCo binary execution data to XML/testwise coverage formats
- **teamscale-client** - HTTP client library for Teamscale server communication
- **tia-client** - Test Impact Analysis client for identifying impacted tests

### Build Tool Plugins

- **teamscale-gradle-plugin** - Gradle plugin for coverage collection and test-wise execution
- **teamscale-maven-plugin** - Maven plugin with equivalent functionality
- **impacted-test-engine** - JUnit 5 engine that queries Teamscale for impacted tests

### Agent Internals

Entry point: `PreMain.premain()` in `agent/src/main/java/com/teamscale/jacoco/agent/PreMain.java`

**Initialization flow:**
1. JVM loads agent via `-javaagent` parameter
2. `PreMain.premain()` parses options and initializes logging
3. `JaCoCoPreMain` registers `LenientCoverageTransformer` with the JVM
4. Classes are instrumented at load time with coverage probes

**Coverage modes:**
- **Interval-based** (`Agent` class) - Periodically dumps coverage (default every 10 min)
- **Test-wise** (`TestwiseCoverageAgent` class) - Per-test coverage via HTTP endpoints (`/test/start`, `/test/end`)

**Key classes:**
- `AgentOptions` - Central configuration holder
- `JacocoRuntimeController` - Interface to JaCoCo's execution data
- `LenientCoverageTransformer` - Bytecode instrumentation with error recovery
- `IUploader` - Interface for upload backends (Teamscale, Azure, Artifactory, disk)

### System Tests

The `system-tests/` directory contains integration tests that exercise the packaged agent JAR in various scenarios. Each subdirectory is a separate Gradle subproject.

## Pre-Commit Checklist

Before completing any code change, verify:

- [ ] CHANGELOG.md updated for user-visible changes (bug fixes, new features, breaking changes)
- [ ] Tests added/updated for the change
- [ ] No unintended files modified
