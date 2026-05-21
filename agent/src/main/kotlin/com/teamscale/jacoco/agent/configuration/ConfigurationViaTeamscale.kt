package com.teamscale.jacoco.agent.configuration

import com.fasterxml.jackson.core.JsonProcessingException
import com.teamscale.client.*
import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.util.AgentUtils
import com.teamscale.report.util.ILogger
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Responsible for holding the configuration retrieved from Teamscale and sending regular heartbeat events to
 * keep the profiler information in Teamscale up to date.
 */
class ConfigurationViaTeamscale(
	private val teamscaleClient: ITeamscaleService,
	profilerRegistration: ProfilerRegistration,
	processInformation: ProcessInformation
) {
	/**
	 * The UUID that Teamscale assigned to this instance of the profiler during the registration. This ID needs to be
	 * used when communicating with Teamscale.
	 */
	@JvmField
	val profilerId = profilerRegistration.profilerId

	private val profilerInfo = ProfilerInfo(processInformation, profilerRegistration.profilerConfiguration)

	/** Returns the profiler configuration retrieved from Teamscale.  */
	val profilerConfiguration: ProfilerConfiguration?
		get() = profilerInfo.profilerConfiguration

	/**
	 * Starts a heartbeat thread and registers a shutdown hook.
	 *
	 *
	 * This spawns a new thread every minute which sends a heartbeat to Teamscale. It also registers a shutdown hook
	 * that unregisters the profiler from Teamscale.
	 */
	fun startHeartbeatThreadAndRegisterShutdownHook() {
		val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
			val thread = Thread(runnable)
			thread.setDaemon(true)
			thread
		}

		executor.scheduleAtFixedRate({ sendHeartbeat() }, 1, 1, TimeUnit.MINUTES)

		Runtime.getRuntime().addShutdownHook(Thread {
			executor.shutdownNow()
			unregisterProfiler()
		})
	}

	private fun sendHeartbeat() {
		try {
			val response = teamscaleClient.sendHeartbeat(profilerId!!, profilerInfo).execute()
			if (!response.isSuccessful) {
				LoggingUtils.getLogger(this).error(
					"Failed to send heartbeat to Teamscale: HTTP ${response.code()} ${response.errorBody()?.string()}." +
							" If heartbeats keep failing, Teamscale will mark this profiler as offline."
				)
			}
		} catch (e: IOException) {
			LoggingUtils.getLogger(this).error("Failed to send heartbeat to Teamscale!", e)
		}
	}

	/** Unregisters the profiler in Teamscale (marks it as shut down).  */
	fun unregisterProfiler() {
		try {
			var response = teamscaleClient.unregisterProfiler(profilerId!!).execute()
			if (response.code() == 405) {
				@Suppress("DEPRECATION")
				response = teamscaleClient.unregisterProfilerLegacy(profilerId).execute()
			}
			if (!response.isSuccessful) {
				LoggingUtils.getLogger(this).error(
					"Failed to unregister profiler with Teamscale: HTTP ${response.code()} ${response.errorBody()?.string()}." +
							" The profiler will be marked offline automatically after the heartbeat timeout."
				)
			}
		} catch (e: IOException) {
			LoggingUtils.getLogger(this).error(
				"Failed to unregister profiler with Teamscale (network error)." +
						" The profiler will be marked offline automatically after the heartbeat timeout.",
				e
			)
		}
	}

	companion object {
		/**
		 * Two minute timeout. This is quite high to account for an eventual high load on the Teamscale server. This is a
		 * tradeoff between fast application startup and potentially missing test coverage.
		 */
		private val LONG_TIMEOUT: Duration = Duration.ofMinutes(2)

		/**
		 * Tries to retrieve the profiler configuration from Teamscale. In case retrieval fails the method throws a
		 * [AgentOptionReceiveException].
		 */
		@Throws(AgentOptionReceiveException::class)
		fun retrieve(
			logger: ILogger,
			configurationId: String?,
			url: HttpUrl,
			userName: String,
			userAccessToken: String
		): ConfigurationViaTeamscale {
			val teamscaleClient = TeamscaleServiceGenerator
				.createService<ITeamscaleService>(url, userName, userAccessToken, AgentUtils.USER_AGENT, LONG_TIMEOUT, LONG_TIMEOUT)
			try {
				val processInformation = ProcessInformationRetriever(logger).processInformation
				val response = teamscaleClient.registerProfiler(
					configurationId,
					processInformation
				).execute()
				if (!response.isSuccessful) {
					throw AgentOptionReceiveException(
						"Failed to retrieve profiler configuration from Teamscale due to failed request. Http status: ${response.code()} Body: ${response.errorBody()?.string()}"
					)
				}

				val body = response.body()
				return parseProfilerRegistration(body!!, response, teamscaleClient, processInformation)
			} catch (e: IOException) {
				throw AgentOptionReceiveException(
					"Failed to retrieve profiler configuration from Teamscale due to network error: ${e.message}." +
							" Verify the Teamscale URL ($url) is reachable from this machine and the configured user has access.",
					e
				)
			}
		}

		@Throws(AgentOptionReceiveException::class, IOException::class)
		private fun parseProfilerRegistration(
			body: ResponseBody,
			response: Response<ResponseBody>,
			teamscaleClient: ITeamscaleService,
			processInformation: ProcessInformation
		): ConfigurationViaTeamscale {
			// We may only call this once
			val bodyString = body.string()
			try {
				val registration = JsonUtils.deserialize<ProfilerRegistration>(bodyString)
				return ConfigurationViaTeamscale(teamscaleClient, registration, processInformation)
			} catch (e: JsonProcessingException) {
				throw AgentOptionReceiveException(
					"Failed to retrieve profiler configuration from Teamscale due to invalid JSON. HTTP code: " + response.code() + " Response: " + bodyString,
					e
				)
			}
		}
	}
}