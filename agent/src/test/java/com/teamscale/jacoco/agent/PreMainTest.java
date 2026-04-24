package com.teamscale.jacoco.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

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
}
