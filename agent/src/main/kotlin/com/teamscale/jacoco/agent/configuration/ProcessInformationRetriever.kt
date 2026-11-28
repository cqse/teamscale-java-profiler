package com.teamscale.jacoco.agent.configuration

import com.teamscale.client.ProcessInformation
import com.teamscale.report.util.ILogger
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Is responsible for retrieving process information such as the host name and process ID.
 */
class ProcessInformationRetriever(private val logger: ILogger) {
	/**
	 * Retrieves the process information, including the host name and process ID.
	 */
	val processInformation: ProcessInformation
		get() = ProcessInformation(hostName, pID, System.currentTimeMillis())

	/**
	 * Retrieves the host name of the local machine.
	 */
	private val hostName: String
		get() {
			try {
				return InetAddress.getLocalHost().hostName
			} catch (e: UnknownHostException) {
				logger.warn(
					"Could not determine the local hostname; using an empty value." +
							" This only affects the display name shown in Teamscale's profiler list.",
					e
				)
				return ""
			}
		}

	companion object {
		/** The PID of the current process. */
		val pID: String
			get() = ProcessHandle.current().pid().toString()
	}
}