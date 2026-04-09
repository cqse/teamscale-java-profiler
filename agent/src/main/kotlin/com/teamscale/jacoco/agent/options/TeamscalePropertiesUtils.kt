package com.teamscale.jacoco.agent.options

import com.teamscale.client.FileSystemUtils.readProperties
import com.teamscale.jacoco.agent.util.AgentUtils.agentDirectory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

/**
 * Utilities for working with the teamscale.properties file that contains access credentials for the Teamscale
 * instance.
 */
object TeamscalePropertiesUtils {
	private val TEAMSCALE_PROPERTIES_PATH = agentDirectory.resolve("teamscale.properties")

	/**
	 * Tries to open [.TEAMSCALE_PROPERTIES_PATH] and parse that properties file to obtain
	 * [TeamscaleCredentials].
	 * 
	 * @return the parsed credentials or null in case the teamscale.properties file doesn't exist.
	 * @throws AgentOptionParseException in case the teamscale.properties file exists but can't be read or parsed.
	 */
	@Throws(AgentOptionParseException::class)
	fun parseCredentials() = parseCredentials(TEAMSCALE_PROPERTIES_PATH)

	/**
	 * Same as [.parseCredentials] but testable since the path is not hardcoded.
	 */
	/*package*/
	@JvmStatic
	@Throws(AgentOptionParseException::class)
	fun parseCredentials(
		teamscalePropertiesPath: Path
	): TeamscaleCredentials? {
		if (!teamscalePropertiesPath.exists()) {
			return null
		}

		try {
			val properties = readProperties(teamscalePropertiesPath.toFile())
			return parseProperties(properties)
		} catch (e: IOException) {
			throw AgentOptionParseException("Failed to read $teamscalePropertiesPath", e)
		}
	}

	@Throws(AgentOptionParseException::class)
	private fun parseProperties(properties: Properties): TeamscaleCredentials {
		val urlString = properties.getProperty("url")
			?: throw AgentOptionParseException("teamscale.properties is missing the url field")

		val url: HttpUrl
		try {
			url = urlString.toHttpUrl()
		} catch (e: IllegalArgumentException) {
			throw AgentOptionParseException("teamscale.properties contained malformed URL $urlString", e)
		}

		val userName = properties.getProperty("username")
			?: throw AgentOptionParseException("teamscale.properties is missing the username field")

		val accessKey = properties.getProperty("accesskey")
			?: throw AgentOptionParseException("teamscale.properties is missing the accesskey field")

		return TeamscaleCredentials(url, userName, accessKey)
	}
}
