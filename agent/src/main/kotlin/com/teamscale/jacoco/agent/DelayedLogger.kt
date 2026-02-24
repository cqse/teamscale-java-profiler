package com.teamscale.jacoco.agent

import com.teamscale.report.util.ILogger
import org.slf4j.Logger
import java.util.function.Consumer

/**
 * A logger that buffers logs in memory and writes them to the actual logger at a later point. This is needed when stuff
 * needs to be logged before the actual logging framework is initialized.
 */
class DelayedLogger : ILogger {
	/** List of log actions that will be executed once the logger is initialized.  */
	private val logActions = mutableListOf<Logger.() -> Unit>()

	override fun debug(message: String) {
		logActions.add { debug(message) }
	}

	override fun info(message: String) {
		logActions.add { info(message) }
	}

	override fun warn(message: String) {
		logActions.add { warn(message) }
	}

	override fun warn(message: String, throwable: Throwable?) {
		logActions.add { warn(message, throwable) }
	}

	override fun error(throwable: Throwable) {
		logActions.add { error(throwable.message, throwable) }
	}

	override fun error(message: String, throwable: Throwable?) {
		logActions.add { error(message, throwable) }
	}

	/**
	 * Logs an error and also writes the message to [System.err] to ensure the message is even logged in case
	 * setting up the logger itself fails for some reason (see TS-23151).
	 */
	fun errorAndStdErr(message: String?, throwable: Throwable?) {
		System.err.println(message)
		logActions.add { error(message, throwable) }
	}

	/** Writes the logs to the given slf4j logger.  */
	fun logTo(logger: Logger) {
		logActions.forEach { action -> action(logger) }
	}
}