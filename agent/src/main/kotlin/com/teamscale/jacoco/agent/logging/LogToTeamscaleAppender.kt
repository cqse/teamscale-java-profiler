package com.teamscale.jacoco.agent.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.status.ErrorStatus
import com.teamscale.client.ITeamscaleService
import com.teamscale.client.ProfilerLogEntry
import com.teamscale.jacoco.agent.options.AgentOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 * Custom log appender that sends logs to Teamscale; it buffers logs that were not sent due to connection issues and
 * sends them later. Uses a [Channel] as a lock-free producer-consumer buffer with a coroutine collector for batching.
 */
class LogToTeamscaleAppender : AppenderBase<ILoggingEvent>() {
	/** The unique ID of the profiler. */
	private var profilerId: String? = null

	/** Lock-free channel for log entries. [Channel.trySend] is called from Logback threads, [Channel.receive] from the collector coroutine. */
	private val logChannel = Channel<ProfilerLogEntry>(capacity = BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

	/** Structured concurrency scope backing the collector coroutine. */
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	/** The collector coroutine job, tracked so [stop] can wait for it to finish. */
	private var collectorJob: Job? = null

	override fun start() {
		super.start()
		collectorJob = scope.launch { collectAndSend() }
	}

	override fun append(eventObject: ILoggingEvent) {
		logChannel.trySend(formatLog(eventObject))
	}

	private fun formatLog(eventObject: ILoggingEvent) = ProfilerLogEntry(
		eventObject.timeStamp,
		eventObject.formattedMessage,
		LoggingUtils.getStackTraceFromEvent(eventObject),
		eventObject.level.toString()
	)

	/**
	 * Collector coroutine: drains [logChannel] into batches, sends them to Teamscale, and retries with backoff on
	 * failure.
	 */
	private suspend fun collectAndSend() {
		val batch = mutableListOf<ProfilerLogEntry>()

		while (currentCoroutineContext().isActive) {
			if (batch.isEmpty()) {
				val entry = withTimeoutOrNull(FLUSH_INTERVAL) {
					logChannel.receive()
				} ?: continue
				batch.add(entry)
			}

			while (batch.size < BATCH_SIZE) {
				logChannel.tryReceive().getOrNull()?.let { batch.add(it) } ?: break
			}

			if (batch.isNotEmpty()) {
				if (sendBatch(batch)) {
					batch.clear()
				} else {
					delay(RETRY_BACKOFF)
				}
			}
		}
	}

	/**
	 * Posts the given [batch] to Teamscale.
	 *
	 * @return `true` if the batch was sent successfully, `false` if it should be retried.
	 */
	private fun sendBatch(batch: List<ProfilerLogEntry>): Boolean {
		val client = teamscaleClient ?: return true
		return try {
			val response = client.postProfilerLog(profilerId!!, batch).execute()
			check(response.isSuccessful) { "Failed to send log: HTTP error code : ${response.code()}" }
			true
		} catch (e: Exception) {
			if (e !is ConnectException) {
				addStatus(ErrorStatus("Sending logs to Teamscale failed: ${e.message}", this, e))
			}
			false
		}
	}

	override fun stop() {
		logChannel.close()
		runBlocking { withTimeoutOrNull(SHUTDOWN_TIMEOUT) { collectorJob?.join() } }
		scope.cancel()
		super.stop()
	}

	fun setTeamscaleClient(teamscaleClient: ITeamscaleService?) {
		Companion.teamscaleClient = teamscaleClient
	}

	fun setProfilerId(profilerId: String) {
		this.profilerId = profilerId
	}

	companion object {
		/** Maximum number of log entries held in memory. Older entries are dropped on overflow. */
		private const val BUFFER_CAPACITY = 10_000

		/** Flush the logs after N elements are in the queue. */
		private const val BATCH_SIZE = 50

		/** Flush the logs in the given time interval. */
		private val FLUSH_INTERVAL: Duration = Duration.ofSeconds(3)

		/** Backoff duration before retrying a failed batch. */
		private val RETRY_BACKOFF: Duration = Duration.ofSeconds(5)

		/** Maximum time to wait for the collector to drain during shutdown. */
		private val SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(3)

		/** The service client for sending logs to Teamscale. */
		private var teamscaleClient: ITeamscaleService? = null

		/**
		 * Add the [LogToTeamscaleAppender] to the logging configuration and
		 * enable/start it.
		 */
		fun addTeamscaleAppenderTo(context: LoggerContext, agentOptions: AgentOptions): Boolean {
			val client = agentOptions.createTeamscaleClient(false)
			if (client == null || agentOptions.configurationViaTeamscale == null) {
				return false
			}

			context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
				val logToTeamscaleAppender = LogToTeamscaleAppender().apply {
					setContext(context)
					setProfilerId(agentOptions.configurationViaTeamscale!!.profilerId!!)
					setTeamscaleClient(client.service)
					start()
				}
				addAppender(logToTeamscaleAppender)
			}

			return true
		}
	}
}
