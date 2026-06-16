package com.teamscale.profiler.installer.steps

import com.teamscale.profiler.installer.*
import com.teamscale.profiler.installer.steps.IStep.IUninstallErrorReporter
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.notExists
import kotlin.io.path.readLines

/**
 * On Linux, registers the agent globally via environment variables set in /etc/environment.
 */
class InstallEtcEnvironmentStep(
	private val etcDirectory: Path,
	private val environmentVariables: JvmEnvironmentMap
) : IStep {

	override fun shouldRun(): Boolean = SystemUtils.IS_OS_LINUX

	private val environmentFile: Path by lazy {
		etcDirectory.resolve("environment")
	}

	/**
	 * Installs the required environment variables in the /etc/environment file.
	 * If the file doesn't exist, it logs a warning and avoids further modification.
	 */
	@Throws(FatalInstallerError::class)
	override fun install(credentials: TeamscaleCredentials) {
		if (environmentFile.notExists()) {
			logMissingEnvironmentFile()
			return
		}

		try {
			val existingLines = Files.readAllLines(environmentFile, StandardCharsets.UTF_8)
			val newLines = generateNewEnvironmentLines(existingLines)

			if (newLines.isNotEmpty()) {
				Files.write(
					environmentFile,
					newLines,
					StandardCharsets.UTF_8,
					StandardOpenOption.APPEND
				)
			}

		} catch (e: IOException) {
			throw PermissionError(
				"Failed to modify ${environmentFile.toAbsolutePath()}." +
						" Ensure the installer is running as root and that no other process is editing the file.",
				e
			)
		}
	}

	/**
	 * Uninstalls any previously added environment variables from the /etc/environment file.
	 */
	override fun uninstall(errorReporter: IUninstallErrorReporter) {
		if (environmentFile.notExists()) return

		try {
			val existingLines = Files.readAllLines(environmentFile, StandardCharsets.UTF_8)
			val cleanedLines = removeProfilerEntries(existingLines)

			Files.write(
				environmentFile,
				cleanedLines,
				StandardCharsets.UTF_8,
				StandardOpenOption.TRUNCATE_EXISTING
			)
		} catch (e: IOException) {
			errorReporter.report(
				PermissionError(
					"Failed to remove profiler entries from ${environmentFile.toAbsolutePath()}. " +
							"Please manually verify and clean up the file to avoid issues.",
					e
				)
			)
		}
	}

	/**
	 * Logs a warning when the /etc/environment file does not exist.
	 */
	private fun logMissingEnvironmentFile() {
		System.err.println(
			"""
            WARNING: ${environmentFile.toAbsolutePath()} does not exist. Skipping system-wide registration of the profiler.
                     You need to manually register the profiler for processes that should be profiled by setting the 
                     following environment variables:
                     
                     ${environmentVariables.etcEnvironmentLinesList.joinToString("\n")}
            """.trimIndent()
		)
	}

	/**
	 * Appends the environment variables only if they don't already exist in the file.
	 */
	private fun generateNewEnvironmentLines(existingLines: List<String>): List<String> {
		val newEntries = environmentVariables.etcEnvironmentLinesList.filter { newEntry ->
			// Only include the new entry if it doesn't exist in the current environment file
			existingLines.none { it.contains(newEntry) }
		}

		return if (newEntries.isNotEmpty()) {
			listOf("\n") + newEntries // Add an empty line for separation before appending entries
		} else {
			emptyList()
		}
	}

	/**
	 * Removes the profiler variables from an existing list of lines.
	 * Handles cases where variables are merged improperly into other environment settings.
	 */
	private fun removeProfilerEntries(existingLines: List<String>) =
		existingLines.filter { line ->
			environmentVariables.etcEnvironmentLinesList.none { variable ->
				// Ensure variable is completely absent in the existing line
				line.contains(variable)
			}
		}
}