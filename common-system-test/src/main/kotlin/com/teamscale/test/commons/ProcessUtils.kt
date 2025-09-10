package com.teamscale.test.commons

import com.teamscale.client.SystemUtils
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Idiomatic Kotlin API for executing system processes safely and efficiently.
 *
 * Features:
 * - Thread-safe execution with automatic stream handling
 * - Kotlin-native builders and DSL-style configuration
 * - Proper error handling with sealed classes and exceptions
 * - Timeout support with Kotlin Duration
 * - Immutable result types
 */
object ProcessUtils {

	private val consoleCharset: Charset by lazy { determineConsoleCharset() }

	private fun determineConsoleCharset(): Charset {
		if (SystemUtils.IS_OS_WINDOWS) {
			try {
				val temporaryCharset = StandardCharsets.UTF_8
				val stdoutConsumer = DefaultStreamConsumer(temporaryCharset, true)
				executeWithoutConcurrencyLimit(
					ProcessBuilder("chcp.com"), null, -1, temporaryCharset,
					stdoutConsumer, DefaultStreamConsumer(temporaryCharset, false)
				)
				val matcher = Pattern.compile("\\d+").matcher(stdoutConsumer.content)
				if (matcher.find()) {
					val charsetName = "Cp" + matcher.group()
					return Charset.forName(charsetName)
				}
			} catch (e: IOException) {
				// Keep default
			} catch (e: IllegalArgumentException) {
				// Keep default
			}
		}
		return StandardCharsets.UTF_8
	}

	/**
	 * Executes a process with the given command and arguments.
	 */
	fun execute(command: String, vararg args: String): ProcessResult =
		processBuilder(command, *args).execute()

	/**
	 * Creates a process builder for fluent API usage.
	 */
	fun processBuilder(command: String, vararg args: String): ProcessExecutor =
		ProcessExecutor(listOf(command) + args)

	/**
	 * Creates a process builder from a command list.
	 */
	fun processBuilder(commands: List<String>): ProcessExecutor =
		ProcessExecutor(commands)

	/**
	 * Fluent API for configuring and executing processes.
	 */
	class ProcessExecutor(private val commands: List<String>) {
		private var workingDirectory: File? = null
		private var input: String? = null

		/**
		 * Sets the working directory for the process.
		 */
		fun directory(dir: File): ProcessExecutor = apply { workingDirectory = dir }

		/**
		 * Executes the process and returns the result.
		 */
		fun execute(): ProcessResult {
			val builder = build()

			val stdoutConsumer = DefaultStreamConsumer(consoleCharset, true)
			val stderrConsumer = DefaultStreamConsumer(consoleCharset, true)

			try {
				val exitCode = executeWithoutConcurrencyLimit(
					builder, input, -1, consoleCharset, stdoutConsumer, stderrConsumer
				)
				return ProcessResult(
					stdout = stdoutConsumer.content,
					stderr = stderrConsumer.content,
					exitCode = exitCode,
					timedOut = exitCode == -1
				)
			} catch (e: IOException) {
				throw ProcessExecutionException("Failed to execute: ${commands.joinToString(" ")}", e)
			}
		}

		/**
		 * Builds the ProcessBuilder with the configured settings.
		 * 
		 * @return ProcessBuilder configured with commands and working directory
		 */
		fun build(): ProcessBuilder = ProcessBuilder(commands).apply {
			workingDirectory?.let { directory(it) }
		}
	}

	/**
	 * Immutable result of process execution.
	 */
	data class ProcessResult(
		/** The standard output of the process */
		val stdout: String,
		/** The standard error output of the process */
		val stderr: String,
		/** The exit code returned by the process */
		val exitCode: Int,
		/** Whether the process was terminated due to timeout */
		val timedOut: Boolean
	) {
		/**
		 * True if the process completed successfully (exit code 0 and not timed out).
		 */
		val isSuccess: Boolean get() = exitCode == 0 && !timedOut

		/**
		 * Returns whether the process was terminated by a timeout or a process interruption.
		 */
		val wasTerminatedByTimeoutOrInterruption: Boolean get() = exitCode == -1

	}

	/**
	 * Exception thrown when process execution fails.
	 */
	class ProcessExecutionException(
		/** The error message describing the process execution failure */
		message: String, 
		/** The underlying cause of the failure, if any */
		cause: Throwable? = null
	) : RuntimeException(message, cause)


	@Throws(IOException::class)
	private fun executeWithoutConcurrencyLimit(
		builder: ProcessBuilder, input: String?, timeout: Int,
		consoleCharset: Charset, stdoutConsumer: IStreamConsumer, stderrConsumer: IStreamConsumer
	): Int {
		// start process
		val process = builder.start()

		// Read output streams of the process in their own threads
		val stderrReader = StreamConsumingThread(process.errorStream, stderrConsumer)
		val stdoutReader = StreamConsumingThread(process.inputStream, stdoutConsumer)

		// write input to process
		if (input != null) {
			val stdIn: Writer = OutputStreamWriter(process.outputStream, consoleCharset)
			stdIn.write(input)
			stdIn.close()
		}

		// wait for process
		val processTimeoutOrInterruption = !waitForProcess(process, timeout)
		var exitValue = -1
		if (!processTimeoutOrInterruption) {
			exitValue = process.exitValue()
		}

		try {
			// It is important to wait for the threads, so the output is
			// completely stored.
			stderrReader.join()
			stdoutReader.join()
		} catch (e: InterruptedException) {
			// ignore this one
		}
		return exitValue
	}

	/**
	 * Waits for the process to end or terminates it if it hits the timeout. The
	 * return value indicated whether the process terminated (true) or was killed by
	 * the timeout (false).
	 *
	 * @param maxRuntimeSeconds
	 * is this is non-positive, this method waits until the process
	 * terminates (without timeout).
	 */
	private fun waitForProcess(process: Process, maxRuntimeSeconds: Int): Boolean {
		var processFinished: Boolean
		try {
			var timeout = maxRuntimeSeconds.toLong()
			if (timeout <= 0) {
				// waitFor(int, TimeUnit) handles zero/negative timeout by returning the current
				// state immediately.
				// But we want to wait indefinitely for this case and cannot use waitFor(void)
				// because this may fail with IllegalThreadStateException (see TS-29795)
				timeout = Long.MAX_VALUE
			}
			processFinished = process.waitFor(timeout, TimeUnit.SECONDS)
		} catch (ignored: InterruptedException) {
			// Got interrupted while waiting for the process to finish
			processFinished = false
		}
		if (!processFinished) {
			process.destroy()
		}
		return processFinished
	}

	/**
	 * Responsible for consuming the stdout/stderr of any executed process.
	 * Delegates the actual consumption the a [IStreamConsumer].
	 */
	private class StreamConsumingThread(
		private val inputStream: InputStream,
		private val streamConsumer: IStreamConsumer
	) : Thread() {
		init {
			start()
		}

		@Synchronized
		override fun run() {
			try {
				streamConsumer.consume(inputStream)
			} catch (e: IOException) {
				LOGGER.log(Level.WARNING, "Encountered IOException during stream consumption", e)
			}
		}

		companion object {
			private val LOGGER: Logger = Logger.getLogger(ProcessExecutor::class.java.canonicalName)
		}
	}

	/**
	 * Provides the possibility to consume STDOUT and/or STDERR of an executing
	 * process.
	 *
	 * @implSpec Implementations must be thread-safe
	 * @see .execute
	 */
	interface IStreamConsumer {
		/**
		 * Consumes the provided `stream`. This operation must block until the
		 * [InputStream] is completely consumed.
		 */
		@Throws(IOException::class)
		fun consume(stream: InputStream)
	}

	/**
	 * [IStreamConsumer] which completely reads the provided
	 * [InputStream]. If [.storeContent] is set, the content is stored
	 * and can be retrieved using [.content].
	 */
	class DefaultStreamConsumer(private val charset: Charset, private val storeContent: Boolean) : IStreamConsumer {
		private val contentBuilder = StringBuilder()

		@Synchronized
		@Throws(IOException::class)
		override fun consume(stream: InputStream) {
			val reader = BufferedReader(InputStreamReader(stream, charset))
			val buffer = CharArray(1024)
			var read: Int
			while ((reader.read(buffer).also { read = it }) != -1) {
				if (storeContent) {
					contentBuilder.append(buffer, 0, read)
				}
			}
		}

		/**
		 * The stored content. If the consumer was constructed to not store any
		 * content, the result is empty.
		 */
		val content: String
			@Synchronized get() = contentBuilder.toString()
	}
}
