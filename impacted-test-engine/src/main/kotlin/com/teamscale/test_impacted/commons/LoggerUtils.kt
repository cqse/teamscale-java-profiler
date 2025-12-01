package com.teamscale.test_impacted.commons

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.*

/**
 * Provides access to a JUL Logger which is configured to print to the console in a not too noisy format as this appears
 * in the console when executing tests.
 */
object LoggerUtils {
	private val MAIN_LOGGER = Logger.getLogger("com.teamscale")
	private const val JAVA_UTIL_LOGGING_CONFIG_FILE_SYSTEM_PROPERTY = "java.util.logging.config.file"

	init {
		// Needs to be at the very top, so it also takes effect when setting the log level for Console handlers
		useDefaultJULConfigFile()

		MAIN_LOGGER.useParentHandlers = false
		val handler = ConsoleHandler()
		handler.formatter = object : SimpleFormatter() {
			@Synchronized
			override fun format(lr: LogRecord) =
				String.format("[%1\$s] %2\$s%n", lr.level.localizedName, lr.message)
		}
		handler.level = Level.WARNING
		MAIN_LOGGER.addHandler(handler)
	}

	/**
	 * Configures file-based logging for the impacted test engine with the specified log level.
	 *
	 * @param logLevel The minimum log level that will be written to the log file.
	 * @param logFilePath The filesystem path where the log file will be written. If null, no file logging is configured.
	 */
	fun configureFileLogging(logLevel: Level, logFilePath: String?) {
		if (logFilePath == null) return
		try {
			val fileHandler = FileHandler(logFilePath, true)
			fileHandler.level = logLevel
			fileHandler.formatter = object : SimpleFormatter() {
				@Synchronized
				override fun format(lr: LogRecord) =
					String.format(
						"%1\$tF %1\$tT [%2\$s] %3\$s: %4\$s%n",
						lr.millis,
						lr.level.localizedName,
						lr.loggerName,
						lr.message
					)
			}
			MAIN_LOGGER.addHandler(fileHandler)
			MAIN_LOGGER.level = logLevel
		} catch (e: IOException) {
			val logger = createLogger()
			logger.warning(
				"Cannot create log file at $logFilePath specified via teamscale.test.impacted.logFilePath: ${e.message}"
			)
		}
	}

	/**
	 * Normally, the java util logging framework picks up the config file specified via the system property
	 * {@value #JAVA_UTIL_LOGGING_CONFIG_FILE_SYSTEM_PROPERTY}. For some reason, this does not work here, so we need to
	 * teach the log manager to use it.
	 */
	private fun useDefaultJULConfigFile() {
		val loggingPropertiesFilePathString =
			System.getProperty(JAVA_UTIL_LOGGING_CONFIG_FILE_SYSTEM_PROPERTY)
				?: return

		val logger = createLogger()
		try {
			val propertiesFilePath = Paths.get(loggingPropertiesFilePathString)
			if (!propertiesFilePath.toFile().exists()) {
				logger.warning(
					"Cannot find the file specified via $JAVA_UTIL_LOGGING_CONFIG_FILE_SYSTEM_PROPERTY: $loggingPropertiesFilePathString"
				)
				return
			}
			LogManager.getLogManager().readConfiguration(Files.newInputStream(propertiesFilePath))
		} catch (e: IOException) {
			logger.warning(
				"Cannot load the file specified via $JAVA_UTIL_LOGGING_CONFIG_FILE_SYSTEM_PROPERTY: $loggingPropertiesFilePathString. ${e.message}"
			)
		}
	}

	/**
	 * Creates a logger for the given class.
	 */
	fun Any.createLogger(): Logger = Logger.getLogger(this::class.java.name)
}
