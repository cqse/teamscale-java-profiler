# Debugging the Teamscale Java Profiler

This document is for developers working **on** the profiler.
For debugging a profiler *deployment*, see the [official documentation](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/).

- [Debugging the agent in the IDE](#debugging-the-agent-in-the-ide)
- [Where the profiler writes its logs](#where-the-profiler-writes-its-logs)
- [End-to-end against a real Teamscale](#end-to-end-against-a-real-teamscale)
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
whatever you are chasing without breaking anything.

### Useful breakpoints

| Where                                  | Fires when                                                                                                                                                 |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PreMain.premain`                      | Once, at JVM startup. Good entry point for anything option- or startup-related.                                                                            |
| `AgentOptionsParser.parse`             | Once, while options are being merged. See [configuration resolution](#how-the-configuration-is-resolved).                                                  |
| `Agent.dumpReport`                     | On every dump: interval, `POST /dump`, and JVM shutdown.                                                                                                   |
| `LenientCoverageTransformer.transform` | **For every single loaded class.** Always make this breakpoint conditional, e.g. `classname.startsWith("com/example")`, or the JVM will not make progress. |

## Where the profiler writes its logs

By default, the agent logs into a **new temporary directory per process**:

```
<java.io.tmpdir>/teamscale-java-profiler-<pid>-<random>/logs/teamscale-jacoco-agent.log
```

The path is announced with a `Logging to ...` line — but that line is written *into that very log file*, so there is
no way to find the log unless you already know where it is. Options, in increasing order of convenience:

- `debug=true` — DEBUG level to both the file **and** the console. The console appender is what makes the log
  discoverable.
- `debug=<dir>` — same, but the file lands in `<dir>/logs` instead of a temporary directory.
- `logging-config=<file.xml>` — full control via a logback configuration. `agent/src/dist/logging/` contains ready-made
  configurations (`logback.console.xml`, `logback.debug.xml`, `logback.rolling-file.xml`).

`sample-app/jacocoagent.properties` uses `logging-config=../agent/src/dist/logging/logback.console.xml`, which
is why the sample app prints the profiler's log to the console instead of hiding it in a temp directory.

The default file appender rolls at 1 MB and keeps 10 compressed files. On a chatty application the startup lines —
usually the most interesting ones — are the first to be rolled away, so capture them early.

## End-to-end against a real Teamscale

The profiler talks to Teamscale for four different things — registration, configuration retrieval, coverage upload, and
log forwarding — and each can fail on its own. This is how to get all of them running locally.

### 1. A Teamscale instance

A local instance is assumed to be reachable at `http://127.0.0.1:9999/teamscale/`.

### 2. An access key

The REST API does **not** accept your password; it needs an access key. Log in, then go to the avatar in the top-right
corner → **Access Keys** (`/user/access-key`) → **Generate New Access Key**, and copy the key.

### 3. A project

Coverage is always uploaded into a project, so one has to exist. Create a project that analyses your checkout of this
repository, so that the sample application's source file (`sample-app/src/main/java/com/example/Main.java`)
is known to Teamscale and the uploaded coverage has something to attach itself to. Note the project ID — that is what
goes into `teamscale-project`, not the display name.

### 4. Configure the sample application

Do not put credentials into the committed `jacocoagent.properties`. Copy it instead:

```bash
cp sample-app/jacocoagent.properties sample-app/jacocoagent.local.properties
```

`jacocoagent.local.properties` is git-ignored, and the `run` task prefers it over `jacocoagent.properties` when it
exists. Fill in:

```properties
includes=*com.example.*
logging-config=../agent/src/dist/logging/logback.console.xml

teamscale-server-url=http://127.0.0.1:9999/teamscale/
teamscale-project=<project id>
teamscale-user=admin
teamscale-access-token=<your access key>
teamscale-partition=Agent Debugging
teamscale-commit=master:HEAD
```

`teamscale-commit` accepts `<branch>:<timestamp>`, and `HEAD` is a valid timestamp. Alternatively use
`teamscale-revision=<git sha>`; the two are mutually exclusive. If you provide neither, the agent tries to auto-detect
the commit from `git.properties` files inside the profiled code — which the sample application does not have.

### 5. Run it

```bash
./gradlew :sample-app:run -Punshaded=true
```

The sample application prints one line and exits immediately. That is enough: `dump-on-exit` defaults to `true`, so the
coverage dump and upload happen during JVM shutdown. You do **not** have to wait for the dump interval, which defaults
to 480 minutes.

Expect this sequence in the console:

```
WARN  Using multiple java agents could interfere with coverage recording: ...
WARN  For best results consider registering the Teamscale Java Profiler first.
INFO  Logging to /var/folders/.../teamscale-java-profiler-<pid>-<random>/logs
INFO  Teamscale Java profiler version <version>
INFO  Starting JaCoCo's agent
INFO  Excluding 23 package prefixes from instrumentation: kotlin.*:shadow.*:...
INFO  Starting Teamscale Java Profiler for process <pid>@<host> with options: config-file=...
INFO  Upload method: Uploading to Teamscale <url> as user <user> for <project> to <partition> at commit <commit>
INFO  Logs are being forwarded to Teamscale at <url>
INFO  Dumping every 480 minutes.
Hello Java Profiler!
INFO  Teamscale Java Profiler is shutting down...
INFO  Teamscale Java Profiler successfully shut down.
```

The two warnings at the top are expected here and not a problem: Gradle attaches its own Java agent to the `run` task,
and it comes first on the command line.

`Upload method:` is the line to check first — it tells you which uploader was actually configured. Without any
`teamscale-*` options it reads `configured output directory on the local disk`, which means nothing will be uploaded
anywhere.

Then in Teamscale, look for the coverage under the partition you configured. If the upload succeeded but you see no
coverage, the upload most likely landed on a commit Teamscale does not know about — check `teamscale-commit`.

### What can go wrong here

- **`The generated coverage report is empty`** — the `includes`/`excludes` patterns did not match anything that ran.
  Widen `includes` first, then narrow it down.
- **No `class-dir` set** — that is fine and is the normal case. The agent then tells JaCoCo to dump the instrumented
  classes into `<temp dir>/jacoco-class-dump` and analyses those (`JacocoAgentOptionsBuilder`). You only need
  `class-dir` when the classes JaCoCo sees differ from the ones you want reported.
- **Nothing at all in the log** — see [When nothing happens at all](#when-nothing-happens-at-all).

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
