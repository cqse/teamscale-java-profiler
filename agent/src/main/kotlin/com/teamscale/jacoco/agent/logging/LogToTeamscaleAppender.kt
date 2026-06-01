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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
	private val logChannel = Channel<ProfilerLogEntry>(Channel.UNLIMITED)

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
	 * Collector coroutine: receives entries from [logChannel], batches them up to [BATCH_SIZE], and sends them
	 * via [sendBatch]. If no entries arrive within [FLUSH_INTERVAL], a new cycle begins (no empty batch is sent).
	 * Exits when the scope is cancelled or the channel is closed and empty.
	 */
	private suspend fun collectAndSend() {
		while (currentCoroutineContext().isActive) {
			val batch = buildList {
				val entry = withTimeoutOrNull(FLUSH_INTERVAL) {
					logChannel.receive()
				} ?: return@buildList
				add(entry)
				repeat(BATCH_SIZE - 1) {
					logChannel.tryReceive().getOrNull()?.let { add(it) } ?: return@buildList
				}
			}
			if (batch.isNotEmpty()) sendBatch(batch)
		}
	}

	/** Posts the given [batch] to Teamscale. On failure, re-enqueues entries back into [logChannel]. */
	private fun sendBatch(batch: List<ProfilerLogEntry>) {
		val client = teamscaleClient ?: return
		try {
			val response = client.postProfilerLog(profilerId!!, batch).execute()
			check(response.isSuccessful) { "Failed to send log: HTTP error code : ${response.code()}" }
		} catch (e: Exception) {
			if (e !is ConnectException) {
				addStatus(ErrorStatus("Sending logs to Teamscale failed: ${e.message}", this, e))
			}
			batch.forEach { logChannel.trySend(it) }
		}
	}

	override fun stop() {
		logChannel.close()
		runBlocking { collectorJob?.join() }
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
		/** Flush the logs after N elements are in the queue. */
		private const val BATCH_SIZE = 50

		/** Flush the logs in the given time interval. */
		private val FLUSH_INTERVAL: Duration = Duration.ofSeconds(3)

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
