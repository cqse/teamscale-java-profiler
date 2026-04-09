package com.teamscale.jacoco.agent.options

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.util.AgentUtils.agentDirectory
import com.teamscale.jacoco.agent.util.AgentUtils.mainTempDirectory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/** Builder for the JaCoCo agent options string.  */
class JacocoAgentOptionsBuilder(private val agentOptions: AgentOptions) {
	private val logger = getLogger(this)

	/**
	 * Returns the options to pass to the JaCoCo agent.
	 */
	@Throws(AgentOptionParseException::class, IOException::class)
	fun createJacocoAgentOptions(): String {
		val builder = StringBuilder(modeSpecificOptions)
		if (agentOptions.jacocoIncludes != null) {
			builder.append(",includes=").append(agentOptions.jacocoIncludes)
		}
		if (agentOptions.jacocoExcludes != null) {
			logger.debug("Using default excludes: ${AgentOptions.DEFAULT_EXCLUDES}")
			builder.append(",excludes=").append(agentOptions.jacocoExcludes)
		}

		// Don't dump class files in testwise mode when coverage is written to an exec file
		val needsClassFiles =
			agentOptions.mode == EMode.NORMAL || agentOptions.testwiseCoverageMode != ETestwiseCoverageMode.EXEC_FILE
		if (agentOptions.classDirectoriesOrZips.isEmpty() && needsClassFiles) {
			val tempDir = createTemporaryDumpDirectory()
			tempDir.toFile().deleteOnExit()
			builder.append(",classdumpdir=").append(tempDir.toAbsolutePath())

			agentOptions.classDirectoriesOrZips = mutableListOf(tempDir.toFile())
		}

		agentOptions.additionalJacocoOptions.forEach { pair ->
			builder.append(",").append(pair.first).append("=").append(pair.second)
		}

		return builder.toString()
	}

	@Throws(AgentOptionParseException::class)
	private fun createTemporaryDumpDirectory(): Path {
		try {
			return Files.createDirectory(mainTempDirectory.resolve("jacoco-class-dump"))
		} catch (_: IOException) {
			logger.warn("Unable to create temporary directory in default location. Trying in system temp directory.")
		}

		try {
			return Files.createTempDirectory("jacoco-class-dump")
		} catch (_: IOException) {
			logger.warn("Unable to create temporary directory in default location. Trying in output directory.")
		}

		try {
			return Files.createTempDirectory(agentOptions.getOutputDirectory(), "jacoco-class-dump")
		} catch (_: IOException) {
			logger.warn("Unable to create temporary directory in output directory. Trying in agent's directory.")
		}

		val agentDirectory = agentDirectory
		try {
			return Files.createTempDirectory(agentDirectory, "jacoco-class-dump")
		} catch (e: IOException) {
			throw AgentOptionParseException("Unable to create a temporary directory anywhere", e)
		}
	}

	/**
	 * Returns additional options for JaCoCo depending on the selected [AgentOptions.mode] and
	 * [AgentOptions.testwiseCoverageMode].
	 */
	@get:Throws(IOException::class)
	val modeSpecificOptions: String
		get() = if (agentOptions.useTestwiseCoverageMode() && agentOptions.testwiseCoverageMode == ETestwiseCoverageMode.EXEC_FILE) {
			// when writing to a .exec file, we can instruct JaCoCo to do so directly
			"destfile=${agentOptions.createNewFileInOutputDirectory("jacoco", "exec").absolutePath}"
		} else {
			// otherwise we don't need JaCoCo to perform any output of the .exec information
			"output=none"
		}
}
