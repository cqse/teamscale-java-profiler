package com.teamscale.profiler.installer

import com.teamscale.profiler.installer.Installer.UninstallerErrorReporter
import com.teamscale.profiler.installer.utils.MockRegistry
import com.teamscale.profiler.installer.utils.MockTeamscale
import com.teamscale.profiler.installer.utils.TestUtils
import com.teamscale.profiler.installer.utils.UninstallErrorReporterAssert
import com.teamscale.test.commons.SystemTestUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

internal class AllPlatformsInstallerTest {
	private lateinit var sourceDirectory: Path
	private lateinit var targetDirectory: Path
	private lateinit var etcDirectory: Path

	private lateinit var installedFile: Path
	private lateinit var installedNestedFile: Path
	private lateinit var installedTeamscaleProperties: Path

	@BeforeEach
	@Throws(IOException::class)
	fun setUpSourceDirectory() {
		sourceDirectory = Files.createTempDirectory("InstallerTest-source")
		targetDirectory = Files.createTempDirectory("InstallerTest-target").resolve("profiler")
		etcDirectory = Files.createTempDirectory("InstallerTest-etc")

		val fileToInstall = sourceDirectory.resolve("install-me.txt")
		Files.writeString(fileToInstall, FILE_TO_INSTALL_CONTENT, StandardOpenOption.CREATE)

		val nestedFileToInstall = sourceDirectory.resolve("lib/teamscale-jacoco-agent.jar")
		Files.createDirectories(nestedFileToInstall.parent)
		Files.writeString(
			nestedFileToInstall, NESTED_FILE_CONTENT,
			StandardOpenOption.CREATE
		)

		installedFile = targetDirectory.resolve(sourceDirectory.relativize(fileToInstall))
		installedNestedFile = targetDirectory.resolve(sourceDirectory.relativize(nestedFileToInstall))
		installedTeamscaleProperties = targetDirectory.resolve("teamscale.properties")
	}

	@Test
	@Throws(FatalInstallerError::class, IOException::class)
	fun successfulInstallation() {
		install()

		Assertions.assertThat(installedFile).exists().content().isEqualTo(FILE_TO_INSTALL_CONTENT)
		Assertions.assertThat(installedNestedFile).exists().content().isEqualTo(NESTED_FILE_CONTENT)
		Assertions.assertThat(installedTeamscaleProperties).exists()

		Properties().apply {
			load(Files.newInputStream(installedTeamscaleProperties))
			Assertions.assertThat(keys).containsExactlyInAnyOrder("url", "username", "accesskey")
			Assertions.assertThat(getProperty("url")).isEqualTo(TEAMSCALE_URL)
			Assertions.assertThat(getProperty("username")).isEqualTo("user")
			Assertions.assertThat(getProperty("accesskey")).isEqualTo("accesskey")
		}
	}

	@Test
	@Throws(IOException::class)
	fun distributionChangedByUser() {
		Files.delete(sourceDirectory.resolve("lib/teamscale-jacoco-agent.jar"))
		Assertions.assertThatThrownBy { install() }
			.hasMessageContaining("It looks like you moved the installer")
	}

	@Test
	@Throws(FatalInstallerError::class)
	fun successfulUninstallation() {
		install()
		val errorReporter = uninstall()
		UninstallErrorReporterAssert.assertThat(errorReporter).hadNoErrors()
	}

	@Test
	fun nonexistantTeamscaleUrl() {
		Assertions.assertThatThrownBy { install("http://does-not-exist:8080") }
			.hasMessageContaining("could not be resolved")
		Assertions.assertThat(targetDirectory).doesNotExist()
	}

	@Test
	fun connectionRefused() {
		Assertions.assertThatThrownBy { install("http://localhost:" + (SystemTestUtils.TEAMSCALE_PORT + 1)) }
			.hasMessageContaining("refused a connection")
		Assertions.assertThat(targetDirectory).doesNotExist()
	}

	@Test
	fun httpsInsteadOfHttp() {
		Assertions.assertThatThrownBy { install("https://localhost:" + SystemTestUtils.TEAMSCALE_PORT) }
			.hasMessageContaining("configured for HTTPS, not HTTP")
		Assertions.assertThat(targetDirectory).doesNotExist()
	}

	@Test
	@Throws(IOException::class)
	fun profilerAlreadyInstalled() {
		Files.createDirectories(targetDirectory)
		Assertions.assertThatThrownBy { install() }.hasMessageContaining("the path already exists")
	}

	@Test
	@Throws(Exception::class)
	fun installDirectoryNotWritable() {
		TestUtils.makePathReadOnly(targetDirectory.parent)
		Assertions.assertThatThrownBy { install(TEAMSCALE_URL) }
			.hasMessageContaining("Cannot create directory")
	}

	@Throws(FatalInstallerError::class)
	private fun install(teamscaleUrl: String = TEAMSCALE_URL) {
		Installer(sourceDirectory, targetDirectory, etcDirectory, false, MockRegistry()).runInstall(
			TeamscaleCredentials(teamscaleUrl.toHttpUrl(), "user", "accesskey")
		)
	}

	private fun uninstall(): UninstallerErrorReporter {
		return Installer(sourceDirectory, targetDirectory, etcDirectory, false, MockRegistry()).runUninstall()
	}

	companion object {
		private const val FILE_TO_INSTALL_CONTENT = "install-me"
		private const val NESTED_FILE_CONTENT = "nested-file"
		private val TEAMSCALE_URL = "http://localhost:" + SystemTestUtils.TEAMSCALE_PORT + "/"

		private var mockTeamscale: MockTeamscale? = null

		@JvmStatic
		@BeforeAll
		fun startFakeTeamscale() {
			mockTeamscale = MockTeamscale(SystemTestUtils.TEAMSCALE_PORT)
		}

		@JvmStatic
		@AfterAll
		fun stopFakeTeamscale() {
			mockTeamscale?.shutdown()
		}
	}
}
