package com.teamscale.jacoco.agent.options

import com.teamscale.jacoco.agent.options.TeamscalePropertiesUtils.parseCredentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class TeamscalePropertiesUtilsTest {
	private var teamscalePropertiesPath: Path? = null

	@BeforeEach
	fun createTempPath(@TempDir tempDir: Path) {
		teamscalePropertiesPath = tempDir.resolve("teamscale.properties")
	}

	@Test
	@Throws(AgentOptionParseException::class)
	fun pathDoesNotExist() {
		Assertions.assertThat(
			parseCredentials(Paths.get("/does/not/exist/teamscale.properties"))
		).isNull()
	}

	@Test
	@Throws(AgentOptionParseException::class, IOException::class)
	fun successfulParsing() {
		Files.write(
			teamscalePropertiesPath!!,
			"url=http://test\nusername=user\naccesskey=key".toByteArray(StandardCharsets.UTF_8)
		)

		val credentials = parseCredentials(teamscalePropertiesPath!!)
		Assertions.assertThat(credentials).isNotNull()
		Assertions.assertThat(credentials!!.url).isEqualTo("http://test".toHttpUrl())
		Assertions.assertThat(credentials.userName).isEqualTo("user")
		Assertions.assertThat(credentials.accessKey).isEqualTo("key")
	}

	@Test
	@Throws(IOException::class)
	fun missingUsername() {
		Files.write(teamscalePropertiesPath!!, "url=http://test\naccesskey=key".toByteArray(StandardCharsets.UTF_8))
		Assertions.assertThatThrownBy { parseCredentials(teamscalePropertiesPath!!) }
			.hasMessageContaining("missing the username")
	}

	@Test
	@Throws(IOException::class)
	fun missingAccessKey() {
		Files.write(teamscalePropertiesPath!!, "url=http://test\nusername=user".toByteArray(StandardCharsets.UTF_8))
		Assertions.assertThatThrownBy { parseCredentials(teamscalePropertiesPath!!) }
			.hasMessageContaining("missing the accesskey")
	}

	@Test
	@Throws(IOException::class)
	fun missingUrl() {
		Files.write(teamscalePropertiesPath!!, "username=user\nusername=user".toByteArray(StandardCharsets.UTF_8))
		Assertions.assertThatThrownBy { parseCredentials(teamscalePropertiesPath!!) }
			.hasMessageContaining("missing the url")
	}

	@Test
	@Throws(IOException::class)
	fun malformedUrl() {
		Files.write(
			teamscalePropertiesPath!!,
			"url=$$**\nusername=user\nusername=user".toByteArray(StandardCharsets.UTF_8)
		)
		Assertions.assertThatThrownBy { parseCredentials(teamscalePropertiesPath!!) }
			.hasMessageContaining("malformed URL")
	}

	/** This test doesn't work on Windows since [java.io.File.setReadable] does not work there.  */
	@DisabledOnOs(OS.WINDOWS)
	@Test
	@Throws(IOException::class)
	fun fileNotReadable() {
		Files.write(
			teamscalePropertiesPath!!,
			"url=http://test\nusername=user\nusername=user".toByteArray(StandardCharsets.UTF_8)
		)
		Assertions.assertThat(teamscalePropertiesPath!!.toFile().setReadable(false)).isTrue()
		Assertions.assertThatThrownBy { parseCredentials(teamscalePropertiesPath!!) }
			.hasMessageContaining("Failed to read")
	}
}