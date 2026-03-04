package com.teamscale.jacoco.agent.util

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.TeamscaleServiceGenerator
import com.teamscale.jacoco.agent.PreMain
import com.teamscale.jacoco.agent.configuration.ProcessInformationRetriever
import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** General utilities for working with the agent.  */
object AgentUtils {
	/** Version of this program.  */
	val VERSION: String

	/** User-Agent header value for HTTP requests.  */
	@JvmField
	val USER_AGENT: String

	/**
	 * Returns the main temporary directory where all agent temp files should be placed.
	 */
	@JvmStatic
	val mainTempDirectory: Path by lazy {
		try {
			// We add a trailing hyphen here to visually separate the PID from the random number that Java appends
			// to the name to make it unique
			Files.createTempDirectory(
				"teamscale-java-profiler-${FileSystemUtils.toSafeFilename(ProcessInformationRetriever.getPID())}-"
			)
		} catch (e: IOException) {
			throw RuntimeException("Failed to create temporary directory for agent files", e)
		}
	}

	/** Returns the directory that contains the agent installation.  */
	@JvmStatic
	val agentDirectory: Path by lazy {
		try {
			val jarFileUri = PreMain::class.java.getProtectionDomain().codeSource.location.toURI()
			// we assume that the dist zip is extracted and the agent jar not moved
			val jarDirectory = Paths.get(jarFileUri).parent
			jarDirectory.parent ?: jarDirectory // happens when the jar file is stored in the root directory
		} catch (e: URISyntaxException) {
			throw RuntimeException("Failed to obtain agent directory. This is a bug, please report it.", e)
		}
	}

	init {
		val bundle = ResourceBundle.getBundle("com.teamscale.jacoco.agent.app")
		VERSION = bundle.getString("version")
		USER_AGENT = TeamscaleServiceGenerator.buildUserAgent("Teamscale Java Profiler", VERSION)
	}
}