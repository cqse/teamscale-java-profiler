package com.teamscale.maven;

import com.teamscale.report.util.ILogger;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Forwards all messages to a mojo's {@link Log}, so Maven applies its log level (e.g. shows debug only with -X). */
public class MavenLogger implements ILogger {

	private final Log log;

	public MavenLogger(Log log) {
		this.log = log;
	}

	@Override
	public void debug(@NotNull String message) {
		log.debug(message);
	}

	@Override
	public void info(@NotNull String message) {
		log.info(message);
	}

	@Override
	public void warn(@NotNull String message) {
		log.warn(message);
	}

	@Override
	public void warn(@NotNull String message, @Nullable Throwable throwable) {
		// Maven's SystemStreamLog throws a NullPointerException for a null throwable
		if (throwable == null) {
			log.warn(message);
		} else {
			log.warn(message, throwable);
		}
	}

	@Override
	public void error(@NotNull Throwable throwable) {
		log.error(throwable);
	}

	@Override
	public void error(@NotNull String message, @Nullable Throwable throwable) {
		if (throwable == null) {
			log.error(message);
		} else {
			log.error(message, throwable);
		}
	}
}
