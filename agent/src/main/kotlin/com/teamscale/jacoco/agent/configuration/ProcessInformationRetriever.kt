package com.teamscale.jacoco.agent.configuration

import com.teamscale.client.ProcessInformation
import com.teamscale.report.util.ILogger
import java.lang.management.ManagementFactory
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
				logger.error("Failed to determine hostname!", e)
				return ""
			}
		}

	/**
	 * Returns a string that *probably* contains the PID.
	 *
	 * On Java 9 there is an API to get the PID. But since we support Java 8, we may fall back to an undocumented API
	 * that at least contains the PID in most JVMs.
	 *
	 * See [This StackOverflow question](https://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id)
	 */
	companion object {
		val pID: String
			get() {
				try {
					val processHandleClass = Class.forName("java.lang.ProcessHandle")
					val processHandle = processHandleClass.getMethod("current").invoke(null)
					val pid = processHandleClass.getMethod("pid").invoke(processHandle) as Long
					return pid.toString()
				} catch (_: ReflectiveOperationException) {
					return ManagementFactory.getRuntimeMXBean().name
				}
			}
	}
}