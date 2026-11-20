package com.teamscale.maven;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MavenLoggerTest {

	private final Log log = mock(Log.class);
	private final MavenLogger logger = new MavenLogger(log);

	@Test
	void forwardsEachLevelToTheMatchingLogMethod() {
		Exception exception = new Exception();

		logger.debug("debug");
		logger.info("info");
		logger.warn("warn");
		logger.warn("warn with exception", exception);
		logger.error(exception);
		logger.error("error with exception", exception);

		verify(log).debug("debug");
		verify(log).info("info");
		verify(log).warn("warn");
		verify(log).warn("warn with exception", exception);
		verify(log).error(exception);
		verify(log).error("error with exception", exception);
		verifyNoMoreInteractions(log);
	}

	/** Maven's default SystemStreamLog throws a NullPointerException if it is passed a null throwable. */
	@Test
	void omitsNullThrowable() {
		Throwable noThrowable = null;

		logger.warn("warn", noThrowable);
		logger.error("error", noThrowable);

		verify(log).warn("warn");
		verify(log).error("error");
		verifyNoMoreInteractions(log);
	}
}
