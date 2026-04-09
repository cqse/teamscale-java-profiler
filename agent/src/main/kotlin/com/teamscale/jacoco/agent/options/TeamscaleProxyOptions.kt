package com.teamscale.jacoco.agent.options

import com.teamscale.client.FileSystemUtils.readFileUTF8
import com.teamscale.client.ProxySystemProperties
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.client.TeamscaleProxySystemProperties
import com.teamscale.report.util.ILogger
import java.io.IOException
import java.nio.file.Path

/**
 * Parses agent command line options related to the proxy settings.
 */
class TeamscaleProxyOptions(private val protocol: ProxySystemProperties.Protocol, private val logger: ILogger) {
	/** The host of the proxy server.  */ /* package */
	@JvmField
	var proxyHost: String?

	/** The port of the proxy server.  */ /* package */
	@JvmField
	var proxyPort: Int = 0

	/** The password for the proxy user.  */ /* package */
	@JvmField
	var proxyPassword: String?

	/** A path to the file that contains the password for the proxy authentication.  */ /* package */
	var proxyPasswordPath: Path? = null

	/** The username of the proxy user.  */ /* package */
	@JvmField
	var proxyUser: String?

	/** Constructor.  */
	init {
		val proxySystemProperties = ProxySystemProperties(protocol)
		proxyHost = proxySystemProperties.proxyHost
		try {
			proxyPort = proxySystemProperties.proxyPort
		} catch (e: ProxySystemProperties.IncorrectPortFormatException) {
			proxyPort = -1
			logger.warn(e.message!!)
		}
		proxyUser = proxySystemProperties.proxyUser
		proxyPassword = proxySystemProperties.proxyPassword
	}

	/**
	 * Processes the command-line options for proxies.
	 * 
	 * @return true if it has successfully processed the given option.
	 */
	@Throws(AgentOptionParseException::class)
	fun handleTeamscaleProxyOptions(key: String?, value: String): Boolean {
		if ("host" == key) {
			proxyHost = value
			return true
		}
		val proxyPortOption = "port"
		if (proxyPortOption == key) {
			try {
				proxyPort = value.toInt()
			} catch (e: NumberFormatException) {
				throw AgentOptionParseException(
					"Could not parse proxy port \"$value\" set via \"$proxyPortOption\"", e
				)
			}
			return true
		}
		if ("user" == key) {
			proxyUser = value
			return true
		} else if ("password" == key) {
			proxyPassword = value
			return true
		}
		return false
	}

	/** Stores the teamscale-specific proxy settings as system properties to make them always available.  */
	fun putTeamscaleProxyOptionsIntoSystemProperties() {
		val teamscaleProxySystemProperties = TeamscaleProxySystemProperties(protocol)
		if (!isEmpty(proxyHost)) {
			teamscaleProxySystemProperties.proxyHost = proxyHost
		}
		if (proxyPort > 0) {
			teamscaleProxySystemProperties.proxyPort = proxyPort
		}
		if (!isEmpty(proxyUser)) {
			teamscaleProxySystemProperties.proxyUser = proxyUser
		}
		if (!isEmpty(proxyPassword)) {
			teamscaleProxySystemProperties.proxyPassword = proxyPassword
		}

		setProxyPasswordFromFile(proxyPasswordPath)
	}

	/**
	 * Sets the proxy password JVM property from a file for the protocol in this instance of
	 * [TeamscaleProxyOptions].
	 */
	private fun setProxyPasswordFromFile(proxyPasswordFilePath: Path?) {
		if (proxyPasswordFilePath == null) {
			return
		}
		try {
			val proxyPassword = readFileUTF8(proxyPasswordFilePath.toFile()).trim()
			TeamscaleProxySystemProperties(protocol).proxyPassword = proxyPassword
		} catch (e: IOException) {
			logger.error(
				"Unable to open file containing proxy password. Please make sure the file exists and the user has the permissions to read the file.",
				e
			)
		}
	}
}
