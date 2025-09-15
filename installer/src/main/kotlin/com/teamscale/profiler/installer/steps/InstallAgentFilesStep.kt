package com.teamscale.profiler.installer.steps

import com.teamscale.profiler.installer.FatalInstallerError
import com.teamscale.profiler.installer.PermissionError
import com.teamscale.profiler.installer.TeamscaleCredentials
import com.teamscale.profiler.installer.steps.IStep.IUninstallErrorReporter
import com.teamscale.profiler.installer.utils.InstallFileUtils
import org.apache.commons.io.file.PathUtils
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

/** Copies the agent into the installation directory and sets the necessary file permissions.  */
class InstallAgentFilesStep(private val sourceDirectory: Path, private val installDirectory: Path) : IStep {
	private val teamscalePropertiesPath: Path
		get() = installDirectory.resolve("teamscale.properties")

	@Throws(FatalInstallerError::class)
	override fun install(credentials: TeamscaleCredentials) {
		ensureAgentIsPresentInSourceDirectory()
		createAgentDirectory()
		copyAgentFiles()
		writeTeamscaleProperties(credentials)
		makeAllProfilerFilesWorldReadable()
	}

	@Throws(FatalInstallerError::class)
	private fun ensureAgentIsPresentInSourceDirectory() {
		val agentPath = sourceDirectory.resolve("lib/teamscale-jacoco-agent.jar")
		if (!Files.exists(agentPath)) {
			throw FatalInstallerError(
				"""
				It looks like you moved the installer. Could not locate the profiler files at $sourceDirectory.
				Please start over by extracting the profiler files from the zip file you downloaded. Do not make any changes to the extracted files and directories or installation will fail.
				""".trimIndent()
			)
		}
	}

	override fun uninstall(errorReporter: IUninstallErrorReporter) {
		if (SystemUtils.IS_OS_WINDOWS) {
			println("Please manually delete $installDirectory")
			return
		}

		if (!Files.exists(installDirectory)) {
			return
		}

		try {
			PathUtils.deleteDirectory(installDirectory)
		} catch (e: IOException) {
			errorReporter.report(FatalInstallerError("Failed to fully delete directory $installDirectory", e))
		}
	}

	@Throws(FatalInstallerError::class)
	private fun makeAllProfilerFilesWorldReadable() {
		try {
			Files.walk(installDirectory).use { fileStream ->
				val it = fileStream.iterator()
				while (it.hasNext()) {
					val path = it.next()
					InstallFileUtils.makeReadable(path)
				}
			}
		} catch (e: IOException) {
			throw PermissionError("Failed to list all files in $installDirectory.", e)
		}
	}

	@Throws(FatalInstallerError::class)
	private fun writeTeamscaleProperties(credentials: TeamscaleCredentials) {
		val properties = Properties().apply {
			setProperty("url", credentials.url.toString())
			setProperty("username", credentials.username)
			setProperty("accesskey", credentials.accessKey)
		}

		try {
			Files.newOutputStream(
				teamscalePropertiesPath, StandardOpenOption.WRITE,
				StandardOpenOption.CREATE
			).use { out ->
				properties.store(out, null)
			}
		} catch (e: IOException) {
			throw PermissionError("Failed to write $teamscalePropertiesPath.", e)
		}

		InstallFileUtils.makeReadable(teamscalePropertiesPath)
	}

	@Throws(FatalInstallerError::class)
	private fun copyAgentFiles() {
		try {
			PathUtils.copyDirectory(sourceDirectory, installDirectory)
		} catch (e: IOException) {
			throw PermissionError(
				("Failed to copy some files to " + installDirectory + "."
						+ " Please manually clean up " + installDirectory), e
			)
		}
	}

	@Throws(FatalInstallerError::class)
	private fun createAgentDirectory() {
		if (Files.exists(installDirectory)) {
			throw FatalInstallerError("Cannot install to $installDirectory: Path already exists")
		}

		InstallFileUtils.createDirectory(installDirectory)
	}
}
