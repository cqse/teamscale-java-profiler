# Debugging the Teamscale Java Profiler

This document is for developers working **on** the profiler.
For debugging a profiler *deployment*, see the [official documentation](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/).

- [Debugging the agent in the IDE](#debugging-the-agent-in-the-ide)
- [Where the profiler writes its logs](#where-the-profiler-writes-its-logs)
- [Running the profiler locally](#running-the-profiler-locally)
- [How the configuration is resolved](#how-the-configuration-is-resolved)
- [Configuring the profiler from Teamscale (`config-id`)](#configuring-the-profiler-from-teamscale-config-id)
- [When nothing happens at all](#when-nothing-happens-at-all)
- [Debugging system tests](#debugging-system-tests)
- [Debugging the Gradle and Maven plugins](#debugging-the-gradle-and-maven-plugins)
- [Debugging the agent from the command line](#debugging-the-agent-from-the-command-line)

## Debugging the agent in the IDE

Use the `SampleApp` run configuration — with _Debug_, not _Run_. It executes `:sample-app:run` with
`-Punshaded=true` and attaches the freshly built agent to a tiny application (`com.example.Main`). Breakpoints
anywhere in the agent sources work; IntelliJ passes the debugger options to the forked application JVM for you.

**`-Punshaded=true` turns off relocation.** The agent is loaded by the same class loader as the application it
profiles, so anything it ships can interfere with that application. The jar therefore carries everything under a
`shadow.` package prefix which keeps the two apart. Those class names do not match what the IDE knows from the source 
tree, so breakpoints would not bind and stack traces would be unreadable. `-Punshaded=true` disables relocation.

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
| `AgentOptionsParser.parse`             | Once, while options are being merged. See [configuration resolution](#how-the-configuration-is-resolved).                                                  |
| `Agent.dumpReport`                     | On every dump: interval, `POST /dump`, and JVM shutdown.                                                                                                   |
| `LenientCoverageTransformer.transform` | **For every single loaded class.** Always make this breakpoint conditional, e.g. `classname.startsWith("com/example")`, or the JVM will not make progress. |

## Where the profiler writes its logs

`sample-app/jacocoagent.properties` sets `debug=true`, so the profiler logs to the console at DEBUG level. Without
that option it logs into a new temporary directory per process.

## Running the profiler locally

Three scenarios, in increasing order of setup. All of them run `sample-app`, which stays alive for ten seconds — long
enough for one dump at shutdown. Pass `-PruntimeSeconds=300` to keep it running for five minutes instead, e.g. to watch
periodic dumps or to take your time in the debugger.

### 1. Only the system under test

```bash
./gradlew :sample-app:run
```

The committed `sample-app/jacocoagent.properties` needs no Teamscale: the profiler instruments `com.example.*`, logs to
the console and writes the reports next to its logs into `<temp dir>/coverage/<timestamp>/`.

### 2. Uploading coverage to Teamscale

Credentials must not end up in the committed file, so copy it — `jacocoagent.local.properties` is git-ignored and the
`run` task prefers it:

```bash
cp sample-app/jacocoagent.properties sample-app/jacocoagent.local.properties
```

Add the server, a project that analyses this repository, and an access key (avatar menu → **Access Keys** →
_Generate New Access Key_; the REST API does not accept your password):

```properties
teamscale-server-url=http://127.0.0.1:8080/
teamscale-project=<project id, not the display name>
teamscale-user=admin
teamscale-access-token=<your access key>
teamscale-partition=Agent Debugging
```

`./gradlew :sample-app:run` then uploads the coverage during JVM shutdown (`dump-on-exit` defaults to `true`); with a
longer runtime, `interval=1` uploads once a minute on top of that. The commit is auto-detected from the
`git.properties` the build generates into the jar, so coverage lands on the revision you have checked out —
**Teamscale has to know that revision** so if you used a File System Connector for example you need to manually specify
 a branch name instead, otherwise the upload is rejected. Commit and let it be analyzed, or set
`teamscale-commit`/`teamscale-revision` explicitly.

### 3. Configuration from the Teamscale profiler configuration UI

In Teamscale, go to _Project Configuration → Coverage Profilers → New profiler configuration → Create for a JVM
project_ and give the configuration these options:

```properties
includes=*com.example.*
interval=1
teamscale-project=<project id, not the display name>
teamscale-partition=Agent Debugging
```

`teamscale-server-url`, `teamscale-user` and `teamscale-access-token` must **not** be among them: the profiler needs
those to fetch the configuration in the first place, so they have to stay local. Reduce
`jacocoagent.local.properties` to:

```properties
debug=true
config-id=<the configuration's ID>
teamscale-server-url=http://127.0.0.1:8080/
teamscale-user=admin
teamscale-access-token=<your access key>
```

While `./gradlew :sample-app:run` is running, the profiler shows up under _Running Profilers_ in Teamscale. See
[configuring the profiler from Teamscale](#configuring-the-profiler-from-teamscale-config-id) for the requests behind
it.

### When it does not work

- **`Upload method:`** is the first line to check — it names the uploader that was actually configured.
  `configured output directory on the local disk` means nothing gets uploaded anywhere;
  `Temporary cache until commit is resolved` turns into a real uploader once `Commit to upload to has been found`
  appears.
- **`The generated coverage report is empty`** — the `includes`/`excludes` patterns did not match anything that ran.
- **Nothing at all in the log** — see [When nothing happens at all](#when-nothing-happens-at-all).
- The two Java-agent warnings at startup are expected: Gradle attaches its own agent to the `run` task.

## How the configuration is resolved

Options come from five places. They are applied in this order, and **later sources overwrite earlier ones**
(`AgentOptionsParser.parse`):

| # | Source                                                                               | Contributes                          |
|---|--------------------------------------------------------------------------------------|--------------------------------------|
| 1 | `teamscale.properties` next to the agent                                             | `url`, `username`, `accesskey` only  |
| 2 | `TEAMSCALE_ACCESS_TOKEN` env var                                                     | the access token only                |
| 3 | The `-javaagent:...=<options>` string                                                | any option, including `config-file=` |
| 4 | `TEAMSCALE_JAVA_PROFILER_CONFIG_ID` env var, then the options fetched from Teamscale | any option                           |
| 5 | `TEAMSCALE_JAVA_PROFILER_CONFIG_FILE` env var                                        | any option                           |

Consequences worth remembering:

- A `config-id` needs `teamscale-server-url`, `teamscale-user` and `teamscale-access-token` to be known **before**
  step 4, i.e. from `teamscale.properties` or the agent options. Otherwise you get an explicit
  `Config-id '...' specified but the following required option(s) are missing: ...`.
- A config file given via the environment (step 5) overrides what Teamscale sent (step 4). The agent logs a warning
  when both are set.
- `teamscale.properties` is looked up at `<agent jar directory>/../teamscale.properties` — that is the *parent* of the
  directory holding the jar, because in the distribution the jar lives in `lib/`. It is **not** a config file; it only
  ever carries credentials. `PreMain` logs a DEBUG message about this because the two are frequently confused.

## Configuring the profiler from Teamscale (`config-id`)

Instead of passing options, the agent can fetch them from Teamscale:

```bash
java -javaagent:teamscale-jacoco-agent.jar=config-id=my-config -jar app.jar
```

The full exchange, all under `api/v2024.7.0/`:

| Step                             | Request                                 | Implemented in                                 |
|----------------------------------|-----------------------------------------|------------------------------------------------|
| Register and fetch configuration | `POST /profilers?configuration-id=<id>` | `ConfigurationViaTeamscale.retrieve`           |
| Heartbeat, once a minute         | `PUT /profilers/<profilerId>`           | `ConfigurationViaTeamscale.sendHeartbeat`      |
| Forward log entries              | `POST /profilers/<profilerId>/logs`     | `LogToTeamscaleAppender`                       |
| Unregister on shutdown           | `DELETE /profilers/<profilerId>`        | `ConfigurationViaTeamscale.unregisterProfiler` |

The server answers the registration with a profiler ID and a `configurationOptions` string, which is a newline-separated
list of the same `key=value` options you would otherwise pass on the command line.

`TeamscaleProfilerConfigurationSystemTest` exercises this whole round trip against `TeamscaleMockServer` and is the
fastest way to see the sequence without a server.

## When nothing happens at all

The profiler deliberately never crashes the application it profiles. That makes several failure modes quiet:

| Situation                                                                          | Behaviour                                                                                                                                                                                                                           |
|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| No options and no `TEAMSCALE_JAVA_PROFILER_CONFIG_ID`/`_CONFIG_FILE`               | `premain` returns immediately, before logging is even initialised. Nothing is logged anywhere. This is intentional: it lets the profiler be registered globally via `JAVA_TOOL_OPTIONS` without profiling every JVM on the machine. |
| Invalid options (`AgentOptionParseException`)                                      | Error is logged, the profiler unregisters itself from Teamscale, and the application starts normally without coverage.                                                                                                              |
| Teamscale unreachable while fetching a `config-id` (`AgentOptionReceiveException`) | Two-minute timeout, then the application starts normally without coverage.                                                                                                                                                          |
| Anything throwing after options were parsed                                        | `PreMain.logStartupFailure` logs it and the application continues.                                                                                                                                                                  |
| Coverage collected but report empty                                                | `EmptyReportException`, logged as a warning on every dump.                                                                                                                                                                          |

So "the application ran fine and there is no coverage" is the expected symptom of almost every misconfiguration. When
in doubt, start with `debug=true` so you at least get console output.

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
  under test, which is the whole point of a system test.
- Only directly spawned `java` processes are affected. JVMs forked by Maven in the Maven-based system tests are not.
- Tests that opt in with `teamscaleAgent(mapOf("debug" to logFilePath))` write the agent's log to their project's
  `logTest/` directory. That directory is wiped at the start of every test run, so copy anything you want to keep.

## Debugging the Gradle and Maven plugins

To try your working copy out in another project, increase the plugin version (`appVersion` in
[build.gradle.kts](../build.gradle.kts)) and run `./gradlew publishToMavenLocal` to deploy it to your local m2 cache.
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

and declaring a plugin dependency on the incremented version.

To attach a debugger:

- **A build that uses the plugin**: `./gradlew --no-daemon -Dorg.gradle.debug=true`. The build pauses and waits for you
  to attach via IntelliJ's _Run → Attach to Process_.
- **The impacted test engine during a build**: `./gradlew --no-daemon --debug-jvm`, then attach once the test phase
  starts.
- Both flags can be combined; the build then pauses twice.

## Debugging the agent from the command line

Seldom needed — the [`SampleApp` run configuration](#debugging-the-agent-in-the-ide) is the usual way to get a
debugger onto the agent. If you do have to start the sample application from a terminal, ask for the debugger
yourself with Gradle's `--debug-jvm`:

```bash
./gradlew :sample-app:run -Punshaded=true --debug-jvm
```

The application JVM then suspends on port 5005 until you attach, e.g. via IntelliJ's _Run → Attach to Process_ or a
Remote JVM Debug configuration. Without `--debug-jvm`, `-Punshaded=true` only turns off relocation — no debugger is
attached and the application simply runs to completion.
