package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.PreMain.premain
import com.teamscale.jacoco.agent.logging.LoggingUtils.loggerContext
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies the cross-cutting design invariant documented on [PreMain]: a failure to start up the profiler
 * (misconfiguration, missing files, environment issues, ...) must never propagate an exception out of
 * [premain], because the JVM would turn that into an
 * `IllegalAgentException` and abort the profiled application.
 */
internal class PreMainTest {
	/**
	 * [premain] sets a system property on its first invocation and early-returns
	 * on every subsequent one. Clearing it before each test ensures every test actually exercises the startup path
	 * instead of silently short-circuiting.
	 */
	@BeforeEach
	fun resetPremainLock() {
		System.clearProperty("TEAMSCALE_JAVA_PROFILER_ATTACHED")
	}

	/**
	 * Stops the logger context so file appenders configured during `premain` release their file handles. Without
	 * this, Windows refuses to delete the `@TempDir` that holds `agent.log` and the test fails during
	 * cleanup.
	 */
	@AfterEach
	fun releaseLogFileHandles() {
		loggerContext.stop()
	}

	/**
	 * Invalid agent options produce an `AgentOptionParseException` internally. The agent must swallow it so the
	 * profiled application keeps running.
	 */
	@Test
	fun premainDoesNotThrowOnInvalidOptions() {
		Assertions.assertThatCode {
			premain(
				"this=is=not=a=valid=option,bogus-key=value",
				null
			)
		}.doesNotThrowAnyException()
	}

	/**
	 * Regression test for TS-43260: a `config-id` without the required Teamscale credentials used to propagate an
	 * `AgentOptionParseException` out of `premain` and abort the profiled application. It must now be
	 * swallowed.
	 */
	@Test
	fun premainDoesNotThrowOnConfigIdWithoutCredentials() {
		Assertions.assertThatCode { premain("config-id=some-config", null) }
			.doesNotThrowAnyException()
	}

	/**
	 * Exercises the top-level `try`/`catch` in [premain] that routes
	 * into `logStartupFailure` — the safety net for failures that surface *after* options have been parsed
	 * (and are therefore not covered by the `AgentOption*Exception` catches inside `startProfiler`).
	 * 
	 * 
	 * The trick: valid options pass parsing, then a `null` [Instrumentation] forces JaCoCo's runtime setup
	 * to dereference `null` (first at `AgentModule.openPackage(inst, …)` inside
	 * `JaCoCoPreMain.createRuntime`). The resulting [NullPointerException] is a stand-in for real-world
	 * post-parse failures — e.g. a Jetty `BindException` when `http-server-port` is taken, or an
	 * `UploaderException` during uploader construction — which would bubble up the same way. In production the
	 * JVM always supplies a non-null `Instrumentation`; passing `null` here is only a cheap way to stage
	 * the failure without a running JVM agent attach.
	 * 
	 * 
	 * A custom `logging-config` points logback at a file inside `tempDir` so we can read back the emitted
	 * error event. A plain [ch.qos.logback.core.read.ListAppender] attached in the test would be detached again
	 * by the `LoggerContext.reset()` that `LoggingUtils.reconfigureLoggerContext` performs during option
	 * parsing.
	 */
	@Test
	@Throws(IOException::class)
	fun premainLogsFailureWhenJaCoCoSetupThrows(@TempDir tempDir: Path) {
		val logFile = tempDir.resolve("agent.log")
		val logbackConfig = tempDir.resolve("logback.xml")
		Files.write(
			logbackConfig, (("<configuration>\n"
					+ "  <appender name=\"FILE\" class=\"ch.qos.logback.core.FileAppender\">\n"
					+ "    <file>" + logFile.toAbsolutePath() + "</file>\n"
					+ "    <encoder><pattern>%-5level %logger - %msg%n%ex</pattern></encoder>\n"
					+ "  </appender>\n"
					+ "  <root level=\"INFO\"><appender-ref ref=\"FILE\"/></root>\n"
					+ "</configuration>\n")).toByteArray(StandardCharsets.UTF_8)
		)

		Assertions.assertThatCode {
			premain("logging-config=" + logbackConfig.toAbsolutePath() + ",includes=com.example.*", null)
		}.doesNotThrowAnyException()

		Assertions.assertThat(logFile).exists()
		val log = String(Files.readAllBytes(logFile), StandardCharsets.UTF_8)
		Assertions.assertThat(log)
			.contains("ERROR")
			.contains("failed to start up")
			.contains("NullPointerException")
	}
}
