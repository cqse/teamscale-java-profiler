package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.options.AgentOptions
import com.sun.net.httpserver.HttpServer
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig
import org.jacoco.agent.rt.RT
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Base class for agent implementations. Handles logger shutdown, store creation and instantiation of the
 * [JacocoRuntimeController].
 *
 *
 * Subclasses must handle dumping onto disk and uploading via the configured uploader.
 */
abstract class AgentBase(
	/** The agent options.  */
	var options: AgentOptions
) {
	/** The logger.  */
	val logger: Logger = LoggingUtils.getLogger(this)

	/** Controls the JaCoCo runtime. */
	val controller: JacocoRuntimeController

	private lateinit var server: HttpServer

	/** Daemon thread pool that handles incoming HTTP requests. */
	private var serverExecutor: ExecutorService? = null

	/**
	 * Lazily generated string representation of the command line arguments to print to the log.
	 */
	private val optionsObjectToLog by lazy {
		object {
			override fun toString(): String =
				if (options.obfuscateSecurityRelatedOutputs) {
					options.obfuscatedOptionsString
				} else {
					options.originalOptionsString
				} ?: ""
		}
	}

	init {
		try {
			controller = JacocoRuntimeController(RT.getAgent())
		} catch (e: IllegalStateException) {
			throw IllegalStateException("Teamscale Java Profiler not started or there is a conflict with another agent on the classpath.", e)
		}
		logger.info(
			"Starting Teamscale Java Profiler for process {} with options: {}",
			ManagementFactory.getRuntimeMXBean().name, optionsObjectToLog
		)
		options.httpServerPort?.let { port ->
			try {
				initServer()
			} catch (e: Exception) {
				logger.error("Could not start http server on port $port. Please check if the port is blocked.")
				throw IllegalStateException("Control server not started.", e)
			}
		}
	}

	/**
	 * Starts the http server, which waits for information about started and finished tests.
	 */
	@Throws(Exception::class)
	private fun initServer() {
		val port = options.httpServerPort
		require(port != null) { "Port must be set." }

		logger.info("Listening for test events on port {}.", port)

		// start = false so we can attach a daemon executor and start it from a daemon thread.
		val baseUri = URI.create("http://localhost:$port/")
		server = JdkHttpServerFactory.createHttpServer(baseUri, initResourceConfig(), false)

		// Use daemon threads to handle requests so they never block JVM shutdown.
		val executor = Executors.newFixedThreadPool(10) { runnable ->
			Thread(runnable).apply { isDaemon = true }
		}
		serverExecutor = executor
		server.executor = executor

		// The HttpServer's internal dispatcher thread inherits its daemon flag from the thread that
		// calls start(). premain runs on a non-daemon thread, so we start the server from a daemon
		// thread to avoid keeping the profiled JVM alive (see HttpServerShutdownSystemTest).
		val starter = Thread { server.start() }.apply { isDaemon = true }
		starter.start()
		starter.join()
	}

	/**
	 * Initializes the [ResourceConfig] used by the embedded Jersey HTTP server.
	 */
	protected abstract fun initResourceConfig(): ResourceConfig

	/**
	 * Registers a shutdown hook that stops the timer and dumps coverage a final time.
	 */
	fun registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(Thread {
			try {
				logger.info("Teamscale Java Profiler is shutting down...")
				stopServer()
				prepareShutdown()
				logger.info("Teamscale Java Profiler successfully shut down.")
			} catch (e: Exception) {
				logger.error("Exception during profiler shutdown.", e)
			} finally {
				// Try to flush logging resources also in case of an exception during shutdown
				PreMain.closeLoggingResources()
			}
		})
	}

	/** Stop the http server if it's running  */
	fun stopServer() {
		options.httpServerPort?.let {
			try {
				server.stop(0)
			} catch (e: Exception) {
				logger.error("Could not stop server so it is killed now.", e)
			} finally {
				serverExecutor?.shutdownNow()
			}
		}
	}

	/** Called when the shutdown hook is triggered.  */
	protected open fun prepareShutdown() {
		// Template method to be overridden by subclasses.
	}

	/**
	 * Dumps the current execution data, converts it, writes it to the output
	 * directory defined in [.options] and uploads it if an uploader is
	 * configured. Logs any errors, never throws an exception.
	 */
	abstract fun dumpReport()
}