package com.teamscale.jacoco.agent;

import org.junit.jupiter.api.AfterEach;
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

	@BeforeEach
	@AfterEach
	void clearLockingProperty() {
		// premain sets this property to prevent double-attach. Clear it so each test runs against a fresh state.
		System.clearProperty(PreMain.LOCKING_SYSTEM_PROPERTY);
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
}
