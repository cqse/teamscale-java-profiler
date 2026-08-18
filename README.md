# Teamscale Java Profiler [![Build Status](https://github.com/cqse/teamscale-jacoco-agent/workflows/Build/badge.svg)](https://github.com/cqse/teamscale-jacoco-agent/actions)

## Download

* [Binary Distribution](https://github.com/cqse/teamscale-jacoco-agent/releases)
* [Docker Container](https://hub.docker.com/r/cqse/teamscale-jacoco-agent/tags/)

## Documentation

* [Teamscale Java Profiler](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/)
* [Teamscale Gradle Plugin](https://docs.teamscale.com/reference/integrations/gradle-plugin/)
* [Teamscale Maven Plugin](https://docs.teamscale.com/reference/integrations/maven-plugin/)

## Architecture

The profiler is a JVM agent that uses [JaCoCo](https://www.jacoco.org/) under the hood for bytecode instrumentation and coverage recording. 
The diagram below shows the data flow from JVM startup to coverage upload.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  JVM                                                                     │
│                                                                          │
│  -javaagent:teamscale-jacoco-agent.jar=...                               │
│       │                                                                  │
│       ▼                                                                  │
│  ┌──────────┐     ┌─────────────────────┐     ┌──────────────────────┐   │
│  │ PreMain  │────▶│ JaCoCoPreMain       │────▶│ LenientCoverage-     │   │
│  │          │     │                     │     │ Transformer          │   │
│  │ Parses   │     │ Creates JaCoCo      │     │                      │   │
│  │ options, │     │ runtime, registers  │     │ Registered with JVM  │   │
│  │ logging  │     │ class transformer   │     │ via addTransformer() │   │
│  └──────────┘     └─────────────────────┘     └──────────┬───────────┘   │
│       │                                                  │               │
│       │           ┌──────────────────────────────────────────────────┐   │
│       │           │  Class Loading                                   │   │
│       │           │                                                  │   │
│       │           │  For every class loaded by the JVM:              │   │
│       │           │  1. Transformer receives original bytecode       │   │
│       │           │  2. JaCoCo injects boolean[] probes at branches  │   │
│       │           │     and lines                                    │   │
│       │           │  3. Modified bytecode returned to JVM            │   │
│       │           │                                                  │   │
│       │           │  Instrumentation happens ONLY at class load      │   │
│       │           │  time. Already-loaded classes are never          │   │
│       │           │  retransformed.                                  │   │
│       │           └──────────────────────────────────────────────────┘   │
│       │                                                  │               │
│       │           ┌──────────────────────────────────────────────────┐   │
│       │           │  Runtime                                         │   │
│       │           │                                                  │   │
│       │           │  Probes fire during normal code execution.       │   │
│       │           │  Each probe sets a flag in a per-class           │   │
│       │           │  boolean[], tracking which lines/branches ran.   │   │
│       │           │  Data accumulates in JaCoCo runtime memory.      │   │
│       │           └──────────────────────────────────────────────────┘   │
│       │                                                                  │
│       ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐     │
│  │  Agent (Normal mode)         OR   TestwiseCoverageAgent         │     │
│  │                                                                 │     │
│  │  HTTP server (Jetty + Jersey) for control:                      │     │
│  │    POST /dump ─── trigger coverage dump                         │     │
│  │    POST /test/start, /test/end ─── per-test coverage            │     │
│  └─────────────────────────────────────────────────────────────────┘     │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
         │
         │  Periodically (default: every 480 min) or on HTTP request
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Coverage Dump Pipeline                                                 │
│                                                                         │
│  1. JacocoRuntimeController.dumpAndReset()                              │
│     Retrieves execution data from JaCoCo runtime and resets probes.     │
│                                                                         │
│  2. JaCoCoXmlReportGenerator.convertSingleDumpToReport()                │
│     Reads class files (from auto-created dump dir or user-specified     │
│     class-dir), matches them with execution data by CRC64 class ID,     │
│     and produces a JaCoCo XML coverage report.                          │
│                                                                         │
│  3. IUploader.upload()                                                  │
│     Sends the XML report to the configured destination.                 │
│                                                                         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌───────────┐  ┌────────────┐
        │Teamscale │  │Artifactory│  │Local disk  │
        │  Server  │  │ / Azure   │  │            │
        └──────────┘  └───────────┘  └────────────┘
```

## Development

Before starting development, please enable the pre-commit hook by running:

```
git config --local core.hooksPath .githooks
```

### Build locally

* Import in IntelliJ as Gradle project
* Command line: `./gradlew assemble`
* Local docker build: `docker build -f agent/src/docker/Dockerfile .`

### Debug locally

Debug the `SampleApp` run configuration in IntelliJ to debug the included `sample-app` with breakpoints in the agent working.

**[docs/DEBUGGING.md](docs/DEBUGGING.md) is the full guide**: setting up end-to-end communication with a real Teamscale
instance, how the agent's configuration is resolved from its five sources, where the profiler writes its logs, why a
misconfigured profiler fails silently, and how to attach a debugger to system tests.

### Contributing

* Create a JIRA issue for changes
* Use pull requests. Complete the "Definition of Done" for every pull request.
* There's a Teamscale project, please fix all findings before submitting your pull request for review. The Teamscale coding guidelines and Definition of Done apply as far as possible with the available tooling.

### Publishing

When master has accumulated changes you want to release, please perform the following on master in a single commit:

- Update [the changelog](CHANGELOG.md) and move all changes from the _Next release_ section to a new version, e.g., `21.3.0`.
- Update the [build.gradle.kts](build.gradle.kts)'s `appVersion` accordingly.
- Commit and push your changes.
- Create a GitHub Release tag with the same version number and the text from the changelog.

Releases are numbered according to semantic versioning (see full [changelog](CHANGELOG.md)).

All tags are built automatically using [GitHub Actions](https://github.com/cqse/teamscale-jacoco-agent/actions) with the release binaries being uploaded to the GitHub Releases, Maven Central, Gradle Plugin Portal and DockerHub.

Only use GitHub releases in production. This ensures that we always know which code is running in production.

### Compiling for a different JaCoCo version

* change `jacoco` in `gradle/libs.versions.toml`
* `./gradlew dist`
* **Do not commit unless you are upgrading to a newer version!**
