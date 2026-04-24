package com.teamscale.jacoco.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the cross-cutting design invariant documented on {@link PreMain}: a failure to start up the profiler
 * (misconfiguration, missing files, environment issues, ...) must never propagate an exception out of
 * {@link PreMain#premain(String, Instrumentation)}, because the JVM would turn that into an
 * {@code IllegalAgentException} and abort the profiled application.
 */
class PreMainTest {

	/**
	 * {@link PreMain#premain(String, Instrumentation)} sets a system property on its first invocation and early-returns
	 * on every subsequent one. Clearing it before each test ensures every test actually exercises the startup path
	 * instead of silently short-circuiting.
	 */
	@BeforeEach
	void resetPremainLock() {
		System.clearProperty("TEAMSCALE_JAVA_PROFILER_ATTACHED");
	}

	/**
	 * Invalid agent options produce an {@code AgentOptionParseException} internally. The agent must swallow it so the
	 * profiled application keeps running.
	 */
	@Test
	void premainDoesNotThrowOnInvalidOptions() {
		assertThatCode(() -> PreMain.premain("this=is=not=a=valid=option,bogus-key=value", null))
				.doesNotThrowAnyException();
	}

	/**
	 * Regression test for TS-43260: a {@code config-id} without the required Teamscale credentials used to propagate an
	 * {@code AgentOptionParseException} out of {@code premain} and abort the profiled application. It must now be
	 * swallowed.
	 */
	@Test
	void premainDoesNotThrowOnConfigIdWithoutCredentials() {
		assertThatCode(() -> PreMain.premain("config-id=some-config", null))
				.doesNotThrowAnyException();
	}

	/**
	 * Exercises the top-level {@code try}/{@code catch} in {@link PreMain#premain(String, Instrumentation)} that routes
	 * into {@code logStartupFailure} — the safety net for failures that surface <em>after</em> options have been parsed
	 * (and are therefore not covered by the {@code AgentOption*Exception} catches inside {@code startProfiler}).
	 * <p>
	 * The trick: valid options pass parsing, then a {@code null} {@link Instrumentation} forces JaCoCo's runtime setup
	 * to dereference {@code null} (first at {@code AgentModule.openPackage(inst, …)} inside
	 * {@code JaCoCoPreMain.createRuntime}). The resulting {@link NullPointerException} is a stand-in for real-world
	 * post-parse failures — e.g. a Jetty {@code BindException} when {@code http-server-port} is taken, or an
	 * {@code UploaderException} during uploader construction — which would bubble up the same way. In production the
	 * JVM always supplies a non-null {@code Instrumentation}; passing {@code null} here is only a cheap way to stage
	 * the failure without a running JVM agent attach.
	 * <p>
	 * A custom {@code logging-config} points logback at a file inside {@code tempDir} so we can read back the emitted
	 * error event. A plain {@link ch.qos.logback.core.read.ListAppender} attached in the test would be detached again
	 * by the {@code LoggerContext.reset()} that {@code LoggingUtils.reconfigureLoggerContext} performs during option
	 * parsing.
	 */
	@Test
	void premainLogsFailureWhenJaCoCoSetupThrows(@TempDir Path tempDir) throws IOException {
		Path logFile = tempDir.resolve("agent.log");
		Path logbackConfig = tempDir.resolve("logback.xml");
		Files.write(logbackConfig, ("<configuration>\n"
				+ "  <appender name=\"FILE\" class=\"ch.qos.logback.core.FileAppender\">\n"
				+ "    <file>" + logFile.toAbsolutePath() + "</file>\n"
				+ "    <encoder><pattern>%-5level %logger - %msg%n%ex</pattern></encoder>\n"
				+ "  </appender>\n"
				+ "  <root level=\"INFO\"><appender-ref ref=\"FILE\"/></root>\n"
				+ "</configuration>\n").getBytes(StandardCharsets.UTF_8));

		assertThatCode(() -> PreMain.premain(
				"logging-config=" + logbackConfig.toAbsolutePath() + ",includes=com.example.*", null))
				.doesNotThrowAnyException();

		assertThat(logFile).exists();
		String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
		assertThat(log)
				.contains("ERROR")
				.contains("failed to start up")
				.contains("NullPointerException");
	}
}
