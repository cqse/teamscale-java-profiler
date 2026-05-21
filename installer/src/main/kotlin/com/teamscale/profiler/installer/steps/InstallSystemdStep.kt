package com.teamscale.profiler.installer.steps

import com.teamscale.profiler.installer.*
import com.teamscale.profiler.installer.steps.IStep.IUninstallErrorReporter
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * On Linux with systemd, registers the agent globally for systemd services. This is necessary in addition to
 * [InstallEtcEnvironmentStep], since systemd doesn't always inject /etc/environment into started services.
 */
class InstallSystemdStep(
	private val etcDirectory: Path,
	private val environmentVariables: JvmEnvironmentMap,
	private val reloadSystemdDaemon: Boolean
) : IStep {
	override fun shouldRun() = SystemUtils.IS_OS_LINUX

	@Throws(FatalInstallerError::class)
	override fun install(credentials: TeamscaleCredentials) {
		if (!Files.exists(systemdEtcDirectory)) {
			println("systemd could not be detected. Not installing profiler for systemd services.")
			// system has no systemd installed
			return
		}

		if (!Files.exists(systemdSystemConfDDirectory)) {
			try {
				Files.createDirectories(systemdSystemConfDDirectory)
			} catch (e: IOException) {
				throw PermissionError(
					"Cannot create the systemd 'system.conf.d' directory at $systemdSystemConfDDirectory" +
							" (${e.message}). Run the installer as root.",
					e
				)
			}
		}

		val systemdConfigFile = systemdConfigFile
		if (Files.exists(systemdConfigFile)) {
			throw PermissionError(
				"""
				Cannot create systemd configuration file $systemdConfigFile because it already exists.
				Please uninstall any old profiler versions first
				""".trimIndent()
			)
		}

		val content = """
			[Manager]
			DefaultEnvironment=${environmentVariables.systemdString}
			""".trimIndent()
		try {
			Files.writeString(systemdConfigFile, content)
		} catch (e: IOException) {
			throw PermissionError(
				"Could not create the systemd configuration file at $systemdConfigFile (${e.message})." +
						" Run the installer as root and ensure /etc/systemd/system.conf.d is writable.",
				e
			)
		}

		daemonReload()
	}

	private fun daemonReload() {
		if (!this.reloadSystemdDaemon) {
			return
		}

		try {
			val builder =
				ProcessBuilder("systemctl", "daemon-reload") // must redirect program output or it might hang forever
					.redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
					.redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null")))

			val process = builder.start()
			if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
				// timeout
				process.destroyForcibly()
				askUserToManuallyReloadDaemon()
			}
		} catch (e: IOException) {
			e.printStackTrace()
			askUserToManuallyReloadDaemon()
		} catch (e: InterruptedException) {
			e.printStackTrace()
			askUserToManuallyReloadDaemon()
		}
	}

	private fun askUserToManuallyReloadDaemon() {
		System.err.println(
			"""
			Failed to reload the systemd daemon. Systemd services can only be profiled after reloading the daemon.
			Please manually reload the daemon with:
			systemctl daemon-reload
			""".trimIndent()
		)
	}

	private val systemdEtcDirectory: Path
		get() = etcDirectory.resolve("systemd")

	private val systemdSystemConfDDirectory: Path
		get() = systemdEtcDirectory.resolve("system.conf.d")

	private val systemdConfigFile: Path
		get() = systemdSystemConfDDirectory.resolve("teamscale-java-profiler.conf")

	override fun uninstall(errorReporter: IUninstallErrorReporter) {
		val systemdConfigFile = systemdConfigFile
		if (!Files.exists(systemdConfigFile)) {
			return
		}

		try {
			Files.delete(systemdConfigFile)
		} catch (e: IOException) {
			errorReporter.report(
				PermissionError("Failed to remove systemd config file $systemdConfigFile. Manually remove this file or systemd Java services may fail to start.", e)
			)
		}

		daemonReload()
	}
}
