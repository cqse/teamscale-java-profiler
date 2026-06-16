package com.teamscale.jacoco.agent.options

import com.teamscale.client.FileSystemUtils.readProperties
import com.teamscale.jacoco.agent.util.AgentUtils.agentDirectory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
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
	@Throws(AgentOptionParseException::class)
	fun parseCredentials(
		teamscalePropertiesPath: Path
	): TeamscaleCredentials? {
		if (!teamscalePropertiesPath.exists()) {
			return null
		}

		try {
			val properties = readProperties(teamscalePropertiesPath.toFile())
			return parseProperties(properties, teamscalePropertiesPath)
		} catch (e: IOException) {
			throw AgentOptionParseException("Failed to read $teamscalePropertiesPath", e)
		}
	}

	@Throws(AgentOptionParseException::class)
	private fun parseProperties(properties: Properties, teamscalePropertiesPath: Path): TeamscaleCredentials {
		val urlString = properties.getProperty("url")
			?: throw AgentOptionParseException(
				"teamscale.properties at $teamscalePropertiesPath is missing the 'url' field." +
						" Add a line like 'url=https://teamscale.example.com/'."
			)

		val url: HttpUrl
		try {
			url = urlString.toHttpUrl()
		} catch (e: IllegalArgumentException) {
			throw AgentOptionParseException(
				"teamscale.properties at $teamscalePropertiesPath contains a malformed URL '$urlString'." +
						" Expected format: 'https://teamscale.example.com/'.",
				e
			)
		}

		val userName = properties.getProperty("username")
			?: throw AgentOptionParseException(
				"teamscale.properties at $teamscalePropertiesPath is missing the 'username' field." +
						" Add a line like 'username=alice'."
			)

		val accessKey = properties.getProperty("accesskey")
			?: throw AgentOptionParseException(
				"teamscale.properties at $teamscalePropertiesPath is missing the 'accesskey' field." +
						" Add a line like 'accesskey=<your-Teamscale-access-key>'."
			)

		return TeamscaleCredentials(url, userName, accessKey)
	}
}
