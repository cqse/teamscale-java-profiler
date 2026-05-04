package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.PreMain.premain
import com.teamscale.jacoco.agent.logging.LoggingUtils.loggerContext
import com.teamscale.jacoco.agent.options.AgentOptionParseException
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies the design invariants documented on [PreMain]:
 *   - Configuration errors (unknown options, missing credentials) propagate out of [premain] so the JVM reports them.
 *   - Runtime failures (port conflicts, uploader errors) are logged and swallowed so the profiled application
 *     continues running without coverage collection.
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
	 * Invalid agent options are configuration errors. The agent must propagate them out of [premain] so the JVM
	 * reports the misconfiguration immediately.
	 */
	@Test
	fun premainPropagatesInvalidOptions() {
		Assertions.assertThatThrownBy {
			premain("this=is=not=a=valid=option,bogus-key=value", null)
		}.isInstanceOf(AgentOptionParseException::class.java)
			.hasMessageContaining("Unknown option: this")
	}

	/**
	 * A `config-id` without the required Teamscale credentials is a configuration error.
	 * The agent must propagate it so the user gets immediate feedback about the missing credentials.
	 */
	@Test
	fun premainPropagatesConfigIdWithoutCredentials() {
		Assertions.assertThatThrownBy { premain("config-id=some-config", null) }
			.isInstanceOf(AgentOptionParseException::class.java)
			.hasMessageContaining("Config-id")
	}

	/**
	 * Exercises the runtime-failure safety net: failures that surface *after* options have been parsed
	 * (e.g. Jetty [java.net.BindException], [UploaderException]) must be logged and swallowed so the
	 * profiled application keeps running.
	 *
	 * The trick: valid options pass parsing, then a `null` [Instrumentation] forces JaCoCo's runtime setup
	 * to dereference `null` (inside `JaCoCoPreMain.createRuntime`). The resulting [NullPointerException] is a
	 * stand-in for real-world post-parse failures. In production the JVM always supplies a non-null
	 * `Instrumentation`; passing `null` here is only a cheap way to stage the failure without a
	 * running JVM agent attach.
	 *
	 * A custom `logging-config` points logback at a file inside `tempDir` so we can read back the emitted
	 * error event.
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

		assertThatCode {
			premain("logging-config=" + logbackConfig.toAbsolutePath() + ",includes=com.example.*", null)
		}.doesNotThrowAnyException()

		assertThat(logFile).exists()
		val log = String(Files.readAllBytes(logFile), StandardCharsets.UTF_8)
		assertThat(log)
			.contains("ERROR")
			.contains("failed to start up")
	}
}
