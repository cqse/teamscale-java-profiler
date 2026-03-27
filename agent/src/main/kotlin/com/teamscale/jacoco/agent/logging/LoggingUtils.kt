package com.teamscale.jacoco.agent.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter
import com.teamscale.jacoco.agent.Agent
import com.teamscale.jacoco.agent.util.NullOutputStream
import com.teamscale.report.util.ILogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.lang.AutoCloseable
import java.nio.file.Path

/**
 * Helps initialize the logging framework properly.
 */
object LoggingUtils {
	/** Returns a logger for the given object's class.  */
	@JvmStatic
	fun getLogger(obj: Any): Logger = LoggerFactory.getLogger(obj.javaClass)

	/** Returns a logger for the given class.  */
	@JvmStatic
	fun getLogger(obj: Class<*>): Logger = LoggerFactory.getLogger(obj)

	/** Initializes the logging to the default configured in the Jar.  */
	fun initializeDefaultLogging(): LoggingResources {
		val stream = Agent::class.java.getResourceAsStream("logback-default.xml")
		reconfigureLoggerContext(stream)
		return LoggingResources()
	}

	/**
	 * Returns the logger context.
	 */
	val loggerContext: LoggerContext
		get() = LoggerFactory.getILoggerFactory() as LoggerContext

	/**
	 * Extracts the stack trace from an ILoggingEvent using ThrowableProxyUtil.
	 *
	 * @param event the logging event containing the exception
	 * @return the stack trace as a String, or null if no exception is associated
	 */
	fun getStackTraceFromEvent(event: ILoggingEvent) =
		event.throwableProxy?.let { ThrowableProxyUtil.asString(it) }

	/**
	 * Converts a Throwable to its stack trace as a String.
	 *
	 * @param throwable the throwable to convert
	 * @return the stack trace as a String
	 */
	@JvmStatic
	fun getStackTraceAsString(throwable: Throwable?) =
		throwable?.let { ThrowableProxyUtil.asString(ThrowableProxy(it)) }

	/**
	 * Reconfigures the logger context to use the configuration XML from the given input stream. Cf. [https://logback.qos.ch/manual/configuration.html](https://logback.qos.ch/manual/configuration.html)
	 */
	private fun reconfigureLoggerContext(stream: InputStream?) {
		StatusPrinter.setPrintStream(PrintStream(NullOutputStream()))
		try {
			val configurator = JoranConfigurator()
			configurator.setContext(loggerContext)
			loggerContext.reset()
			configurator.doConfigure(stream)
		} catch (_: JoranException) {
			// StatusPrinter will handle this
		}
		StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext)
	}

	/**
	 * Initializes the logging from the given file. If that is `null`, uses [ ][.initializeDefaultLogging] instead.
	 */
	@Throws(IOException::class)
	fun initializeLogging(loggingConfigFile: Path?): LoggingResources {
		if (loggingConfigFile == null) {
			return initializeDefaultLogging()
		}

		reconfigureLoggerContext(FileInputStream(loggingConfigFile.toFile()))
		return LoggingResources()
	}

	/** Initializes debug logging.  */
	fun initializeDebugLogging(logDirectory: Path?): LoggingResources {
		if (logDirectory != null) {
			DebugLogDirectoryPropertyDefiner.filePath = logDirectory
		}
		val stream = Agent::class.java.getResourceAsStream("logback-default-debugging.xml")
		reconfigureLoggerContext(stream)
		return LoggingResources()
	}

	/** Wraps the given slf4j logger into an [com.teamscale.report.util.ILogger].  */
	@JvmStatic
	fun wrap(logger: Logger): ILogger {
		return object : ILogger {
			override fun debug(message: String) = logger.debug(message)
			override fun info(message: String) = logger.info(message)
			override fun warn(message: String) = logger.warn(message)
			override fun warn(message: String, throwable: Throwable?) = logger.warn(message, throwable)
			override fun error(throwable: Throwable) = logger.error(throwable.message, throwable)
			override fun error(message: String, throwable: Throwable?) = logger.error(message, throwable)
		}
	}

	/** Class to use with try-with-resources to close the logging framework's resources.  */
	class LoggingResources : AutoCloseable {
		override fun close() {
			loggerContext.stop()
		}
	}
}