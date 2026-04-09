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
         │  Periodically (default: every 10 min) or on HTTP request
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

For IntelliJ, there is a run config `SampleApp` which profiles the included `sample-debugging-app` and can be used to 
debug the agent. This run config omits relocating packages in the shadow jar because with relocating, the package 
names in the jar would not match with the ones IntelliJ knows from the code and so debugging would not work. 
The agent is configured in the config file `sample-debugging-app/jacocoagent.properties`. By default, no upload 
is configured but the file includes all required options to upload to Teamscale, they are just commented out. 
Feel free to adapt it to your needs.
If you get an `IllegalStateException: Cannot process instrumented class com/example/Main`, make sure that you use 
the built-in IntelliJ functionality for building and running instead of 
gradle (IntelliJ Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Build and run using: IntelliJ IDEA).

### Debugging the Gradle plugin

* increase the plugin version (=`appVersion`) in [build.gradle.kts](build.gradle.kts)
* `./gradlew publishToMavenLocal` will deploy your checked out version to your local m2 cache
* then you can import this version into any other gradle project by
  * adding the following to the `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
		mavenLocal()
		gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
		mavenLocal()
        mavenCentral()
    }
}
```
  * declaring a plugin dependency on the incremented version of the teamscale plugin
* to debug a build that uses the plugin, run `./gradlew` with `--no-daemon -Dorg.gradle.debug=true`.
  The build will pause and wait for you to attach a debugger, via IntelliJ's `Run > Attach to Process`.
* to debug the impacted test engine during a build, run `./gradlew` with `--no-daemon --debug-jvm` and wait for the test phase to start.
  The build will pause and wait for you to attach a debugger, via IntelliJ's `Run > Attach to Process`.
* These two debug flags can also be combined. The build will then pause twice.

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
