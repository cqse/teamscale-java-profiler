# Debugging the Teamscale Java Profiler

This document is for developers working **on** the profiler.
For debugging a profiler *deployment*, see the [official documentation](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/).

- [Debugging the agent in the IDE](#debugging-the-agent-in-the-ide)
- [Where the profiler writes its logs](#where-the-profiler-writes-its-logs)
- [Running the profiler locally](#running-the-profiler-locally)
- [Debugging system tests](#debugging-system-tests)
- [Debugging the Gradle and Maven plugins](#debugging-the-gradle-and-maven-plugins)

## Debugging the agent in the IDE

Use the `SampleApp` run configuration. It executes `:sample-app:run` with `-Punshaded=true` and attaches the 
freshly built agent to a tiny application (`com.example.Main`). When started as _Debug_, breakpoints
anywhere in the agent sources work.

**`-Punshaded=true` turns off relocation.** The agent is loaded by the same class loader as the application it
profiles, so anything it ships can interfere with that application. The jar therefore carries everything under a
`shadow.` package prefix which keeps the two apart. Those class names do not match what the IDE knows from the source 
tree, so breakpoints would not bind and stack traces would be unreadable.

The flip side: **this is not the artifact that ships.** Bugs that are caused by relocation itself — a class name
built at runtime, a resource path, Kotlin module metadata — will not reproduce under `-Punshaded=true`. If a problem
disappears when you enable debugging, suspect the shading, and reproduce against a normal `./gradlew :agent:shadowJar`
build.

### Which application to profile

`sample-app` is a playground: nothing in the build depends on it, so you can change it freely to reproduce
whatever you are chasing without breaking anything. Its `run` task profiles the application's jar instead of the class
directories, so that the generated `git.properties` is where the agent looks for it — see
[uploading coverage to Teamscale](#2-uploading-coverage-to-teamscale).

### Useful breakpoints

| Where                                  | Fires when                                                                                                                                                 |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PreMain.premain`                      | Once, at JVM startup. Good entry point for anything option- or startup-related.                                                                            |
| `AgentOptionsParser.parse`             | Once, while options are being merged, so you can see which source contributed which option.                                                                |
| `Agent.dumpReport`                     | On every dump: interval, `POST /dump`, and JVM shutdown.                                                                                                   |
| `LenientCoverageTransformer.transform` | **For every single loaded class.** Always make this breakpoint conditional, e.g. `classname.startsWith("com/example")`, or the JVM will not make progress. |

## Where the profiler writes its logs

`sample-app/java-profiler.properties` sets `debug=true`, so the profiler logs to the console at DEBUG level. Without
that option it logs into a new temporary directory per process.

## Running the profiler locally

Three scenarios, in increasing order of setup, all configured through `sample-app/java-profiler.properties`. Each of
them runs `sample-app`, which stays alive for ten seconds — long enough for one dump at shutdown. Pass
`-PruntimeSeconds=300` to keep it running for five minutes instead, e.g. to take your time in the debugger.

### 1. Only the system under test

```bash
./gradlew :sample-app:run
```

The committed file works as it is, with everything Teamscale-related still commented out: the profiler instruments
`com.example.*`, logs to the console and writes the reports next to its logs into `<temp dir>/coverage/<timestamp>/`.

### 2. Uploading coverage to Teamscale

Credentials must not end up in the committed file, so copy it — `java-profiler.local.properties` is git-ignored and the
`run` task prefers it:

```bash
cp sample-app/java-profiler.properties sample-app/java-profiler.local.properties
```

Then uncomment the connection to Teamscale, plus the project and partition under _Local configuration_. The access key
comes from the avatar menu → **Access Keys** → _Generate New Access Key_; the REST API does not accept your password:

```properties
teamscale-server-url=http://127.0.0.1:8080/
teamscale-user=admin
teamscale-access-token=<your access key>

teamscale-project=<project id, not the display name>
teamscale-partition=Agent Debugging
```

`./gradlew :sample-app:run` then uploads the coverage during JVM shutdown (`dump-on-exit` defaults to `true`); add
`interval=1` to also upload every minute while the application is still running. The commit is auto-detected from the
`git.properties` the build generates into the jar, so coverage lands on the revision you have checked out —
**Teamscale has to know that revision** so if you used a File System Connector for example you need to manually specify
 a branch name instead, otherwise the upload is rejected. Commit and let it be analyzed, or set
`teamscale-commit`/`teamscale-revision` explicitly.

### 3. Configuration from the Teamscale profiler configuration UI

In Teamscale, go to _Project Configuration → Coverage Profilers → New profiler configuration → Create for a JVM
project_:

- Select your project
- Pick an arbitrary partition name, for example Agent Debugging
- Profiled Packages: com.example

Teamscale now supplies exactly what the _Local configuration_ section did, so all that stays in
`java-profiler.local.properties` is the connection — the profiler needs it to fetch the configuration in the first
place — and the ID of the configuration you just created:

```properties
debug=true

teamscale-server-url=http://127.0.0.1:8080/
teamscale-user=admin
teamscale-access-token=<your access key>

config-id=<id of the profiler configuration>
```

Give the application more than the default ten seconds here, so that there is time to watch it register, heartbeat and
unregister:

```bash
./gradlew :sample-app:run -PruntimeSeconds=300
```

While it runs, the profiler shows up under _Running Profilers_ in Teamscale. `ConfigurationViaTeamscale` implements
the requests behind that — registration, heartbeat and unregistration on shutdown.

## Debugging system tests

System tests exercise the **packaged** agent jar, so they catch shading problems that `-Punshaded=true` hides. They
come in two shapes, and they are debugged differently.

**The agent is attached to the Gradle test JVM** (most tests — those calling `teamscaleAgent(...)` in their
`build.gradle.kts`). Use Gradle's built-in flag:

```bash
./gradlew :system-tests:default-excludes-test:test --debug-jvm
```

The build pauses on port 5005 until you attach via IntelliJ's _Run → Attach to Process_ or a Remote JVM Debug
configuration.

**The test spawns its own JVM** (e.g. `teamscale-profiler-configuration-test`, `sut-uses-logback-test`, which call
`ProcessUtils.execute("java", ...)`). `--debug-jvm` only suspends the test JVM, not the spawned one. Use `-PdebugSut`
instead:

```bash
./gradlew :system-tests:teamscale-profiler-configuration-test:test -PdebugSut
```

Every `java` process the test spawns then starts with
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005` and waits for a debugger. Pass a port explicitly
with `-PdebugSut=5006`. Because the spawned JVM suspends until you attach, run a **single** system test at a time.

Notes:

- Combine with `-Punshaded=true` to also get unrelocated class names — but remember that this changes the artifact
  under test
- Only directly spawned `java` processes are affected. JVMs forked by Maven in the Maven-based system tests are not.
- Tests that opt in with `teamscaleAgent(mapOf("debug" to logFilePath))` write the agent's log to their project's
  `logTest/` directory. That directory is wiped at the start of every test run, so copy anything you want to keep.

## Debugging the Gradle and Maven plugins

To try your working copy out in another project, run `./gradlew publishToMavenLocal` to deploy it to your local m2 cache.
The consuming project can then pick it up by adding the following to its `settings.gradle.kts`:

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

and declaring a plugin dependency on the -SNAPSHOT version.

To attach a debugger:

- **A build that uses the plugin**: `./gradlew --no-daemon -Dorg.gradle.debug=true`. The build pauses and waits for you
  to attach via IntelliJ's _Run → Attach to Process_.
- **The impacted test engine during a build**: `./gradlew --no-daemon --debug-jvm`, then attach once the test phase
  starts.
- Both flags can be combined; the build then pauses twice.
