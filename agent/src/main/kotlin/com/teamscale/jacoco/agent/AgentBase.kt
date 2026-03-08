package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.options.AgentOptions
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.jacoco.agent.rt.RT
import org.slf4j.Logger
import java.lang.management.ManagementFactory

/**
 * Base class for agent implementations. Handles logger shutdown, store creation and instantiation of the
 * [JacocoRuntimeController].
 *
 *
 * Subclasses must handle dumping onto disk and uploading via the configured uploader.
 */
abstract class AgentBase(
	/** The agent options.  */
	@JvmField var options: AgentOptions
) {
	/** The logger.  */
	val logger: Logger = LoggingUtils.getLogger(this)

	/** Controls the JaCoCo runtime.  */
	@JvmField
	val controller: JacocoRuntimeController

	private lateinit var server: Server

	/**
	 * Lazily generated string representation of the command line arguments to print to the log.
	 */
	private val optionsObjectToLog by lazy {
		object {
			override fun toString() =
				if (options.shouldObfuscateSecurityRelatedOutputs()) {
					options.getObfuscatedOptionsString()
				} else {
					options.getOriginalOptionsString()
				}
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
		options.getHttpServerPort()?.let { port ->
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
		logger.info("Listening for test events on port {}.", options.getHttpServerPort())

		// Jersey Implementation
		val handler = buildUsingResourceConfig()
		val threadPool = QueuedThreadPool()
		threadPool.maxThreads = 10
		threadPool.isDaemon = true

		// Create a server instance and set the thread pool
		server = Server(threadPool)
		// Create a server connector, set the port and add it to the server
		val connector = ServerConnector(server)
		connector.port = options.getHttpServerPort()
		server.addConnector(connector)
		server.handler = handler
		server.start()
	}

	private fun buildUsingResourceConfig(): ServletContextHandler {
		val handler = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
		handler.contextPath = "/"

		val resourceConfig = initResourceConfig()
		handler.addServlet(ServletHolder(ServletContainer(resourceConfig)), "/*")
		return handler
	}

	/**
	 * Initializes the [ResourceConfig] needed for the Jetty + Jersey Server
	 */
	protected abstract fun initResourceConfig(): ResourceConfig?

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
		options.getHttpServerPort()?.let {
			try {
				server.stop()
			} catch (e: Exception) {
				logger.error("Could not stop server so it is killed now.", e)
			} finally {
				server.destroy()
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