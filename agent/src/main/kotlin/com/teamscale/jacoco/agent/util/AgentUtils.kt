package com.teamscale.jacoco.agent.util

import com.teamscale.client.BugReportMessages
import com.teamscale.client.FileSystemUtils
import com.teamscale.client.TeamscaleServiceGenerator
import com.teamscale.jacoco.agent.PreMain
import com.teamscale.jacoco.agent.configuration.ProcessInformationRetriever
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** General utilities for working with the agent.  */
object AgentUtils {
	private const val JAVAAGENT_ARGUMENT_PREFIX = "-javaagent:"

	/** The agent's JAR, named as the installer ships it or as a build tool resolves it, with a version suffix.  */
	private val AGENT_JAR_PATTERN = Regex("""teamscale-jacoco-agent(-.+)?\.jar""")

	/** Version of this program.  */
	val VERSION: String

	/** User-Agent header value for HTTP requests.  */
	@JvmField
	val USER_AGENT: String

	/**
	 * Returns the main temporary directory where all agent temp files should be placed.
	 */
	val mainTempDirectory: Path by lazy {
		try {
			// We add a trailing hyphen here to visually separate the PID from the random number that Java appends
			// to the name to make it unique
			Files.createTempDirectory(
				"teamscale-java-profiler-${FileSystemUtils.toSafeFilename(ProcessInformationRetriever.pID)}-"
			)
		} catch (e: IOException) {
			throw RuntimeException(
				"Failed to create the agent's temporary directory under java.io.tmpdir" +
						" (${System.getProperty("java.io.tmpdir")})." +
						" Ensure the directory exists, is writable, and has free space.",
				e
			)
		}
	}

	/** Returns the directory that contains the agent installation.  */
	val agentDirectory: Path by lazy {
		// we assume that the dist zip is extracted and the agent jar not moved
		val jarDirectory = agentJarFile().toAbsolutePath().parent
		jarDirectory.parent ?: jarDirectory // happens when the jar file is stored in the root directory
	}

	/** Returns the agent's own JAR, or throws if neither of the two ways of locating it yields a file.  */
	private fun agentJarFile(): Path =
		agentJarFileFromCodeSource()
			?: agentJarFileFromJavaAgentJvmArgument()
			?: throw RuntimeException(
				"Failed to locate the agent's own JAR, neither through its class loader nor the -javaagent argument." +
						" ${BugReportMessages.REPORT_TO_CQSE}"
			)

	/** Returns the agent's JAR as its own class loader records it, or null if that yields no local file.  */
	private fun agentJarFileFromCodeSource(): Path? =
		agentJarFileFromCodeSource(PreMain::class.java.protectionDomain?.codeSource?.location)

	/**
	 * Returns the agent's JAR for the given code source location, or null if the location is absent or names
	 * something other than a local file. Not every class loader records where it loaded a class from, and not
	 * every one loads it from a local file.
	 */
	@VisibleForTesting
	internal fun agentJarFileFromCodeSource(codeSourceLocation: URL?): Path? {
		val jarFileUri = try {
			codeSourceLocation?.toURI() ?: return null
		} catch (_: URISyntaxException) {
			return null
		}
		// Paths.get rejects every other scheme.
		if (jarFileUri.scheme != "file") return null
		return Paths.get(jarFileUri)
	}

	/** Returns the agent's JAR as the JVM's own `-javaagent` arguments name it, or null if none of them names it.  */
	private fun agentJarFileFromJavaAgentJvmArgument(): Path? =
		agentJarFileFromJavaAgentJvmArgument(ManagementFactory.getRuntimeMXBean().inputArguments)

	/** Returns the agent's JAR as the given `-javaagent` arguments name it, or null if none of them names it.  */
	@VisibleForTesting
	internal fun agentJarFileFromJavaAgentJvmArgument(jvmArguments: List<String>): Path? =
		jvmArguments
			.filter { it.startsWith(JAVAAGENT_ARGUMENT_PREFIX) }
			.map { Paths.get(it.removePrefix(JAVAAGENT_ARGUMENT_PREFIX).substringBefore('=')) }
			.filter { AGENT_JAR_PATTERN.matches(it.fileName?.toString().orEmpty()) }
			.firstOrNull()

	init {
		val bundle = ResourceBundle.getBundle("com.teamscale.jacoco.agent.app")
		VERSION = bundle.getString("version")
		USER_AGENT = TeamscaleServiceGenerator.buildUserAgent("Teamscale Java Profiler", VERSION)
	}
}