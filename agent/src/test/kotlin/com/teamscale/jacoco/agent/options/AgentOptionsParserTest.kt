package com.teamscale.jacoco.agent.options

import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.client.ProfilerConfiguration
import com.teamscale.client.ProfilerRegistration
import com.teamscale.jacoco.agent.configuration.AgentOptionReceiveException
import com.teamscale.jacoco.agent.options.AgentOptions.EUploadMethod
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig
import com.teamscale.jacoco.agent.util.TestUtils.cleanAgentCoverageDirectory
import com.teamscale.report.util.CommandLineLogger
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** Tests parsing of the agent's command line options.  */
class AgentOptionsParserTest {
	private var teamscaleCredentials: TeamscaleCredentials? = null
	private var configFile: Path? = null

	/** The mock server to run requests against.  */
	protected var mockWebServer: MockWebServer? = null

	/** Starts the mock server.  */
	@BeforeEach
	@Throws(Exception::class)
	fun setup() {
		configFile = Paths.get(javaClass.getResource("agent.properties")!!.toURI())
		mockWebServer = MockWebServer()
		mockWebServer!!.start()
		teamscaleCredentials = TeamscaleCredentials(mockWebServer!!.url("/"), "user", "key")
	}

	/** Shuts down the mock server.  */
	@AfterEach
	@Throws(Exception::class)
	fun cleanup() {
		mockWebServer!!.shutdown()
	}

	@Test
	@Throws(Exception::class)
	fun testUploadMethodRecognition() {
		Assertions.assertThat(parseAndThrow(null).determineUploadMethod())
			.isEqualTo(EUploadMethod.LOCAL_DISK)
		Assertions.assertThat(parseAndThrow("azure-url=azure.com,azure-key=key").determineUploadMethod())
			.isEqualTo(
				EUploadMethod.AZURE_FILE_STORAGE
			)
		Assertions.assertThat(
			parseAndThrow(
				String.format(
					"%s=%s,%s=%s,%s=%s", ArtifactoryConfig.ARTIFACTORY_URL_OPTION, "http://some_url",
					ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION, "apikey",
					ArtifactoryConfig.ARTIFACTORY_PARTITION, "partition"
				)
			).determineUploadMethod()
		).isEqualTo(EUploadMethod.ARTIFACTORY)

		val basicTeamscaleOptions =
			"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p"
		Assertions.assertThat(
			parseAndThrow(basicTeamscaleOptions)
				.determineUploadMethod()
		).isEqualTo(EUploadMethod.TEAMSCALE_MULTI_PROJECT)
		Assertions.assertThat(
			parseAndThrow("$basicTeamscaleOptions,teamscale-project=proj")
				.determineUploadMethod()
		).isEqualTo(EUploadMethod.TEAMSCALE_SINGLE_PROJECT)
		Assertions.assertThat(
			parseAndThrow(
				"$basicTeamscaleOptions,sap-nwdi-applications=com.package.MyClass:projectId;com.company.Main:project"
			).determineUploadMethod()
		).isEqualTo(EUploadMethod.SAP_NWDI_TEAMSCALE)
	}

	@Test
	@Throws(Exception::class)
	fun testUploadMethodRecognitionWithTeamscaleProperties() {
		Assertions.assertThat(parseAndThrow(null).determineUploadMethod())
			.isEqualTo(EUploadMethod.LOCAL_DISK)
		Assertions.assertThat(parseAndThrow("azure-url=azure.com,azure-key=key").determineUploadMethod())
			.isEqualTo(
				EUploadMethod.AZURE_FILE_STORAGE
			)
		Assertions.assertThat(
			parseAndThrow(
				String.format(
					"%s=%s,%s=%s,%s=%s", ArtifactoryConfig.ARTIFACTORY_URL_OPTION, "http://some_url",
					ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION, "apikey",
					ArtifactoryConfig.ARTIFACTORY_PARTITION, "partition"
				)
			).determineUploadMethod()
		).isEqualTo(EUploadMethod.ARTIFACTORY)

		val basicTeamscaleOptions =
			"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p"
		Assertions.assertThat(
			parseAndThrow(basicTeamscaleOptions)
				.determineUploadMethod()
		).isEqualTo(EUploadMethod.TEAMSCALE_MULTI_PROJECT)
		Assertions.assertThat(
			parseAndThrow("$basicTeamscaleOptions,teamscale-project=proj")
				.determineUploadMethod()
		).isEqualTo(EUploadMethod.TEAMSCALE_SINGLE_PROJECT)
		Assertions.assertThat(
			parseAndThrow(
				"$basicTeamscaleOptions,sap-nwdi-applications=com.package.MyClass:projectId;com.company.Main:project"
			).determineUploadMethod()
		).isEqualTo(EUploadMethod.SAP_NWDI_TEAMSCALE)
	}

	@Test
	@Throws(Exception::class)
	fun environmentConfigIdOverridesCommandLineOptions() {
		val registration = ProfilerRegistration()
		registration.profilerId = UUID.randomUUID().toString()
		registration.profilerConfiguration = ProfilerConfiguration()
		registration.profilerConfiguration!!.configurationId = "my-config"
		registration.profilerConfiguration!!.configurationOptions = "teamscale-partition=foo"
		mockWebServer!!.enqueue(MockResponse().setBody(registration.serializeToJson()))
		val parser = AgentOptionsParser(
			CommandLineLogger(), "my-config",
			null, teamscaleCredentials, null
		)
		val options = parseAndThrow(parser, "teamscale-partition=bar")

		Assertions.assertThat(options.teamscaleServer.partition).isEqualTo("foo")
	}

	@Test
	@Throws(Exception::class)
	fun environmentConfigFileOverridesCommandLineOptions() {
		val parser = AgentOptionsParser(
			CommandLineLogger(), null, configFile.toString(),
			teamscaleCredentials, null
		)
		val options = parseAndThrow(parser, "teamscale-partition=from-command-line")

		Assertions.assertThat(options.teamscaleServer.partition).isEqualTo("from-config-file")
	}

	@Test
	@Throws(Exception::class)
	fun environmentConfigFileOverridesConfigId() {
		val registration = ProfilerRegistration()
		registration.profilerId = UUID.randomUUID().toString()
		registration.profilerConfiguration = ProfilerConfiguration()
		registration.profilerConfiguration!!.configurationId = "my-config"
		registration.profilerConfiguration!!.configurationOptions = "teamscale-partition=from-config-id"
		mockWebServer!!.enqueue(MockResponse().setBody(registration.serializeToJson()))
		val parser = AgentOptionsParser(
			CommandLineLogger(), "my-config", configFile.toString(),
			teamscaleCredentials, null
		)
		val options = parseAndThrow(parser, "teamscale-partition=from-command-line")

		Assertions.assertThat(options.teamscaleServer.partition).isEqualTo("from-config-file")
	}

	@Test
	fun notAllRequiredTeamscaleOptionsSet() {
		Assertions.assertThatCode {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-project=proj"
			)
		}.doesNotThrowAnyException()
		Assertions.assertThatCode {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p"
			)
		}.doesNotThrowAnyException()
		Assertions.assertThatCode {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token"
			)
		}.doesNotThrowAnyException()

		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-project=proj"
			)
		}.hasMessageContaining("You configured a 'teamscale-project' but no 'teamscale-partition' to upload to.")

		Assertions.assertThatThrownBy { parseAndThrow("teamscale-server-url=teamscale.com") }
			.hasMessageContaining("not all required ones")
		Assertions.assertThatThrownBy { parseAndThrow("teamscale-server-url=teamscale.com,teamscale-user=user") }
			.hasMessageContaining("not all required ones")
		Assertions.assertThatThrownBy { parseAndThrow("teamscale-server-url=teamscale.com,teamscale-access-token=token") }
			.hasMessageContaining("not all required ones")
		Assertions.assertThatThrownBy { parseAndThrow("teamscale-user=user,teamscale-access-token=token") }
			.hasMessageContaining("not all required ones")
		Assertions.assertThatThrownBy { parseAndThrow("teamscale-revision=1234") }
			.hasMessageContaining("not all required ones")
		Assertions.assertThatThrownBy { parseAndThrow("teamscale-commit=master:1234") }
			.hasMessageContaining("not all required ones")
	}

	@Test
	fun sapNwdiRequiresAllTeamscaleOptionsExceptProject() {
		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,sap-nwdi-applications=com.package.MyClass:projectId;com.company.Main:project"
			)
		}.hasMessageContaining(
			"You provided an SAP NWDI applications config, but the 'teamscale-' upload options are incomplete"
		)

		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-project=proj,sap-nwdi-applications=com.package.MyClass:projectId;com.company.Main:project"
			)
		}.hasMessageContaining(
			"The project must be specified via sap-nwdi-applications"
		)

		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-project=proj,sap-nwdi-applications=com.package.MyClass:projectId;com.company.Main:project"
			)
		}.hasMessageContaining(
			"You provided an SAP NWDI applications config and a teamscale-project"
		).hasMessageNotContaining("the 'teamscale-' upload options are incomplete")
	}

	/**
	 * Test that we can define a config id first, before adding teamscale server credentials. We still expect an
	 * exception to be thrown, because there is no Teamscale server to reach, but no parse exception.
	 */
	@Test
	fun testConfigIdOptionOrderIrrelevant() {
		Assertions.assertThatThrownBy {
			parseAndThrow(
				"config-id=myConfig,teamscale-server-url=http://awesome-teamscale.com,teamscale-user=user,teamscale-access-token=mycoolkey"
			)
		}.hasMessageNotContaining(
			"Failed to parse agent options"
		).hasMessageContaining(
			"Failed to retrieve profiler configuration from Teamscale"
		)
	}

	@Test
	fun revisionOrCommitRequireProject() {
		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-revision=12345"
			)
		}.hasMessageContaining("you did not provide the 'teamscale-project'")
		Assertions.assertThatThrownBy {
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-commit=master:HEAD"
			)
		}.hasMessageContaining("you did not provide the 'teamscale-project'")
	}

	@Test
	fun testTeamscaleUploadRequiresRevisionOrCommit() {
		val teamscaleBaseOptions =
			"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=token,teamscale-partition=p,teamscale-project=proj,mode=testwise,http-server-port=8123,"
		Assertions.assertThatThrownBy {
			parseAndThrow(
				teamscaleBaseOptions + "tia-mode=teamscale-upload"
			)
		}.hasMessageContaining("You use 'tia-mode=teamscale-upload' but did not provide a revision or commit")

		Assertions.assertThatCode {
			parseAndThrow(
				teamscaleBaseOptions + "tia-mode=teamscale-upload,teamscale-revision=12345"
			)
		}.doesNotThrowAnyException()

		Assertions.assertThatCode {
			parseAndThrow(
				teamscaleBaseOptions + "tia-mode=teamscale-upload,teamscale-commit=master:HEAD"
			)
		}.doesNotThrowAnyException()
	}


	@Test
	fun environmentConfigIdDoesNotExist() {
		mockWebServer!!.enqueue(MockResponse().setResponseCode(404).setBody("invalid-config-id does not exist"))
		Assertions.assertThatThrownBy {
			AgentOptionsParser(
				CommandLineLogger(), "invalid-config-id", null,
				teamscaleCredentials, null
			).parse(
				""
			)
		}.isInstanceOf(AgentOptionReceiveException::class.java).hasMessageContaining("invalid-config-id does not exist")
	}

	@Test
	@Throws(Exception::class)
	fun accessTokenFromEnvironment() {
		Assertions.assertThat(
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-partition=p,teamscale-project=p",
				"envtoken"
			).teamscaleServer.userAccessToken
		).isEqualTo("envtoken")
		// command line overrides env variable
		Assertions.assertThat(
			parseAndThrow(
				"teamscale-server-url=teamscale.com,teamscale-user=user,teamscale-access-token=commandlinetoken,teamscale-partition=p,teamscale-project=p",
				"envtoken"
			).teamscaleServer.userAccessToken
		).isEqualTo("commandlinetoken")
	}

	@Test
	@Throws(Exception::class)
	fun notGivingAnyOptionsShouldBeOK() {
		parseAndThrow("")
		parseAndThrow(null)
	}

	@Test
	@Throws(Exception::class)
	fun mustPreserveDefaultExcludes() {
		Assertions.assertThat(parseAndThrow("").jacocoExcludes).isEqualTo(AgentOptions.DEFAULT_EXCLUDES)
		Assertions.assertThat(parseAndThrow("excludes=**foo**").jacocoExcludes)
			.isEqualTo("**foo**:${AgentOptions.DEFAULT_EXCLUDES}")
	}

	@Test
	@Throws(Exception::class)
	fun teamscalePropertiesCredentialsUsedAsDefaultButOverridable() {
		Assertions.assertThat(
			parseAndThrow(
				AgentOptionsParser(CommandLineLogger(), null, null, teamscaleCredentials, null),
				"teamscale-project=p,teamscale-partition=p"
			).teamscaleServer.userName
		).isEqualTo("user")
		Assertions.assertThat(
			parseAndThrow(
				AgentOptionsParser(CommandLineLogger(), null, null, teamscaleCredentials, null),
				"teamscale-project=p,teamscale-partition=p,teamscale-user=user2"
			).teamscaleServer.userName
		).isEqualTo("user2")
	}

	@Throws(Exception::class)
	private fun parseAndThrow(parser: AgentOptionsParser, options: String?): AgentOptions {
		val result = parser.parse(options)
		parser.throwOnCollectedErrors()
		return result
	}

	@Throws(Exception::class)
	private fun parseAndThrow(options: String?): AgentOptions {
		val parser = AgentOptionsParser(CommandLineLogger(), null, null, null, null)
		return parseAndThrow(parser, options)
	}

	@Throws(Exception::class)
	private fun parseAndThrow(options: String?, environmentAccessToken: String?): AgentOptions {
		val parser = AgentOptionsParser(
			CommandLineLogger(), null, null, null, environmentAccessToken
		)
		return parseAndThrow(parser, options)
	}

	companion object {
		/**
		 * Delete created coverage folders
		 */
		@JvmStatic
		@AfterAll
		@Throws(IOException::class)
		fun teardown() {
			cleanAgentCoverageDirectory()
		}
	}
}
