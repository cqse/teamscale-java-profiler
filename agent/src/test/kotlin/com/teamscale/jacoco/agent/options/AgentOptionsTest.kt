package com.teamscale.jacoco.agent.options

import com.teamscale.client.*
import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.HttpUtils.PROXY_AUTHORIZATION_HTTP_HEADER
import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.jacoco.agent.options.AgentOptionsParser.Companion.putTeamscaleProxyOptionsIntoSystemProperties
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig
import com.teamscale.jacoco.agent.util.TestUtils.cleanAgentCoverageDirectory
import com.teamscale.report.util.CommandLineLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowableAssert
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Predicate

/** Tests the [AgentOptions].  */
class AgentOptionsTest {
	@TempDir
	var testFolder: File? = null

	@BeforeEach
	@Throws(IOException::class)
	fun setUp() {
		File(testFolder, "file_with_manifest1.jar").createNewFile()
		File(testFolder, "plugins/inner").mkdirs()
		File(testFolder, "plugins/some_other_file.jar").createNewFile()
		File(testFolder, "plugins/file_with_manifest2.jar").createNewFile()
	}

	/** Tests include pattern matching.  */
	@Test
	@Throws(Exception::class)
	fun testIncludePatternMatching() {
		Assertions.assertThat(includeFilter("com.*")).accepts(
			"file.jar@com/foo/Bar.class", $$"file.jar@com/foo/Bar$Goo.class",
			"file1.jar@goo/file2.jar@com/foo/Bar.class", "com/foo/Bar.class", "com.foo/Bar.class"
		)
		Assertions.assertThat(includeFilter("com.*")).rejects(
			"foo/com/Bar.class", "com.class", "file.jar@com.class",
			$$"A$com$Bar.class"
		)
		Assertions.assertThat(includeFilter("*com.*")).accepts(
			"file.jar@com/foo/Bar.class", $$"file.jar@com/foo/Bar$Goo.class",
			"file1.jar@goo/file2.jar@com/foo/Bar.class", "com/foo/Bar.class", "foo/com/goo/Bar.class",
			$$"A$com$Bar.class", "src/com/foo/Bar.class"
		)
		Assertions.assertThat(includeFilter("*com.*;*de.*"))
			.accepts("file.jar@com/foo/Bar.class", $$"file.jar@de/foo/Bar$Goo.class")
		Assertions.assertThat(excludeFilter("*com.*;*de.*"))
			.rejects("file.jar@com/foo/Bar.class", $$"file.jar@de/foo/Bar$Goo.class")
		Assertions.assertThat(includeFilter("*com.customer.*")).accepts(
			"C:\\client-daily\\client\\plugins\\com.customer.something.client_1.2.3.4.1234566778.jar@com/customer/something/SomeClass.class"
		)
	}

	/** Interval options test.  */
	@Test
	@Throws(Exception::class)
	fun testIntervalOptions() {
		var agentOptions = parseAndMaybeThrow("")
		Assertions.assertThat(agentOptions.dumpIntervalInMinutes).isEqualTo(480)
		agentOptions = parseAndMaybeThrow("interval=0")
		Assertions.assertThat(agentOptions.shouldDumpInIntervals()).isEqualTo(false)
		agentOptions = parseAndMaybeThrow("interval=30")
		Assertions.assertThat(agentOptions.shouldDumpInIntervals()).isEqualTo(true)
		Assertions.assertThat(agentOptions.dumpIntervalInMinutes).isEqualTo(30)
	}

	/** Tests the options for uploading coverage to teamscale.  */
	@Test
	@Throws(Exception::class)
	fun testTeamscaleUploadOptions() {
		val agentOptions = parseAndMaybeThrow(
			"" +
					"teamscale-server-url=127.0.0.1," +
					"teamscale-project=test," +
					"teamscale-user=build," +
					"teamscale-access-token=token," +
					"teamscale-partition=\"Unit Tests\"," +
					"teamscale-commit=default:HEAD," +
					"teamscale-message=\"This is my message\""
		)

		val teamscaleServer = agentOptions.teamscaleServer
		Assertions.assertThat(teamscaleServer.url.toString()).isEqualTo("http://127.0.0.1/")
		Assertions.assertThat(teamscaleServer.project).isEqualTo("test")
		Assertions.assertThat(teamscaleServer.userName).isEqualTo("build")
		Assertions.assertThat(teamscaleServer.userAccessToken).isEqualTo("token")
		Assertions.assertThat(teamscaleServer.partition).isEqualTo("Unit Tests")
		Assertions.assertThat(teamscaleServer.commit.toString()).isEqualTo("default:HEAD")
		Assertions.assertThat(teamscaleServer.message).isEqualTo("This is my message")
	}

	/** Tests the options for the Test Impact mode.  */
	@Test
	@Throws(Exception::class)
	fun testHttpServerOptions() {
		val agentOptions = parseAndMaybeThrow(
			"mode=TESTWISE,class-dir=.," +
					"http-server-port=8081"
		)
		Assertions.assertThat(agentOptions.httpServerPort).isEqualTo(8081)
	}

	/** Tests the options http-server-port option for normal mode.  */
	@Test
	@Throws(Exception::class)
	fun testHttpServerOptionsForNormalMode() {
		val agentOptions = parseAndMaybeThrow("http-server-port=8081")
		Assertions.assertThat(agentOptions.httpServerPort).isEqualTo(8081)
	}

	/** Tests the options for the Test Impact mode.  */
	@Test
	@Throws(Exception::class)
	fun testHttpServerOptionsWithCoverageViaHttp() {
		val agentOptions = parseAndMaybeThrow(
			"mode=TESTWISE,class-dir=.," +
					"http-server-port=8081,tia-mode=http"
		)
		Assertions.assertThat(agentOptions.httpServerPort).isEqualTo(8081)
		Assertions.assertThat(agentOptions.testwiseCoverageMode)
			.isEqualTo(ETestwiseCoverageMode.HTTP)
	}

	/** Tests setting ignore-uncovered-classes works.  */
	@Test
	@Throws(Exception::class)
	fun testIgnoreUncoveredClasses() {
		val agentOptions = parseAndMaybeThrow("ignore-uncovered-classes=true")
		Assertions.assertThat(agentOptions.ignoreUncoveredClasses).isTrue()
	}

	/** Tests default for ignore-uncovered-classes is false.  */
	@Test
	@Throws(Exception::class)
	fun testIgnoreUncoveredClassesDefault() {
		val agentOptions = parseAndMaybeThrow("")
		Assertions.assertThat(agentOptions.ignoreUncoveredClasses).isFalse()
	}

	/** Tests default for ignore-uncovered-classes is false.  */
	@Test
	@Throws(Exception::class)
	fun shouldAllowMinusForEnumConstants() {
		val agentOptions = parseAndMaybeThrow("tia-mode=exec-file")
		Assertions.assertThat(agentOptions.testwiseCoverageMode)
			.isEqualTo(ETestwiseCoverageMode.EXEC_FILE)
	}

	/** Tests that supplying both revision and commit info is forbidden.  */
	@Test
	@Throws(URISyntaxException::class)
	fun testBothRevisionAndCommitSupplied() {
		val message = ("'teamscale-revision' and 'teamscale-revision-manifest-jar' are incompatible with "
				+ "'teamscale-commit' and 'teamscale-commit-manifest-jar'.")

		val jar = File(javaClass.getResource("manifest-and-git-properties.jar")!!.toURI())

		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-revision=1234,teamscale-commit=master:1000"
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-revision=1234,teamscale-commit-manifest-jar=" + jar.absolutePath
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-revision-manifest-jar=${jar.absolutePath},teamscale-commit=master:1000"
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-revision-manifest-jar=${jar.absolutePath},teamscale-commit-manifest-jar=${jar.absolutePath}"
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
	}

	/** Tests the 'teamscale-revision-manifest-jar' option correctly parses the 'Git_Commit' field in the manifest.  */
	@Test
	@Throws(Exception::class)
	fun testTeamscaleRevisionManifestJarOption() {
		val jar = File(javaClass.getResource("manifest-with-git-commit-revision.jar")!!.toURI())
		val options = parseAndMaybeThrow(
			"teamscale-revision-manifest-jar=${jar.absolutePath},teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p"
		)

		Assertions.assertThat(options.teamscaleServer.revision).isEqualTo("f364d58dc4966ca856260185e46a90f80ee5e9c6")
	}

	/**
	 * Tests that an exception is thrown when the user attempts to specify the commit/revision when 'teamscale-project'
	 * is not specified. The absence of the `teamscale-project` implies a multi-project upload, so the commit/revision
	 * have to be provided individually via the 'git.properties' file.
	 */
	@Test
	@Throws(Exception::class)
	fun testNoCommitOrRevisionGivenWhenProjectNull() {
		val message = "You tried to provide a commit to upload to directly." +
				" This is not possible, since you did not provide the 'teamscale-project' to upload to"

		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-server-url=127.0.0.1," +
						"teamscale-user=build," +
						"teamscale-access-token=token," +
						"teamscale-partition=\"Unit Tests\"," +
						"teamscale-commit=default:HEAD," +
						"teamscale-message=\"This is my message\""
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
		Assertions.assertThatThrownBy {
			parseAndMaybeThrow(
				"teamscale-server-url=127.0.0.1," +
						"teamscale-user=build," +
						"teamscale-access-token=token," +
						"teamscale-partition=\"Unit Tests\"," +
						"teamscale-revision=1234ABCD," +
						"teamscale-message=\"This is my message\""
			)
		}.isInstanceOf(AgentOptionParseException::class.java).hasMessageContaining(message)
	}

	/**
	 * Test that agent continues to run if the user provided an invalid path via the
	 * [AgentOptions.GIT_PROPERTIES_JAR_OPTION] jar option.
	 */
	@Test
	@Throws(Exception::class)
	fun testGitPropertiesJarOptionWithNonExistentFileDoesNotFailBadly() {
		val jarFile = File(javaClass.getResource("nested-jar.war")!!.file)
		val agentOptions = parseAndMaybeThrow(
			"${AgentOptions.GIT_PROPERTIES_JAR_OPTION}=doesNotExist${File.separator}${jarFile.absolutePath}"
		)
		Assertions.assertThat(agentOptions.gitPropertiesJar).isNull()
	}

	/** Test that the [AgentOptions.GIT_PROPERTIES_JAR_OPTION] option can be parsed correctly  */
	@Test
	@Throws(Exception::class)
	fun testGitPropertiesJarOptionParsedCorrectly() {
		val jarFile = File(javaClass.getResource("nested-jar.war")!!.file)
		val agentOptions = parseAndMaybeThrow(
			"${AgentOptions.GIT_PROPERTIES_JAR_OPTION}=${jarFile.absolutePath}"
		)
		Assertions.assertThat(agentOptions.gitPropertiesJar).isNotNull()
	}

	/**
	 * Test that agent continues to run if the user provided a folder via the
	 * [AgentOptions.GIT_PROPERTIES_JAR_OPTION] jar option.
	 */
	@Test
	@Throws(Exception::class)
	fun testGitPropertiesJarDoesNotAcceptFolders() {
		val jarFile = File(javaClass.getResource("nested-jar.war")!!.file)
		val agentOptions = parseAndMaybeThrow(
			"${AgentOptions.GIT_PROPERTIES_JAR_OPTION}=${jarFile.getParent()}"
		)
		Assertions.assertThat(agentOptions.gitPropertiesJar).isNull()
	}

	/** Tests that supplying version info is supported in Testwise mode.  */
	@Test
	@Throws(Exception::class)
	fun testVersionInfosInTestwiseMode() {
		var agentOptions = parseAndMaybeThrow(
			"mode=TESTWISE,class-dir=.,http-server-port=8081,teamscale-revision=1234,teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p"
		)
		Assertions.assertThat(agentOptions.teamscaleServer.revision).isEqualTo("1234")

		agentOptions = parseAndMaybeThrow(
			"mode=TESTWISE,class-dir=.,http-server-port=8081,teamscale-commit=branch:1234,teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p"
		)
		Assertions.assertThat(agentOptions.teamscaleServer.commit).isEqualTo(parse("branch:1234"))
	}

	/** Tests the options for azure file storage upload.  */
	@Test
	@Throws(Exception::class)
	fun testAzureFileStorageOptions() {
		val agentOptions = parseAndMaybeThrow(
			"" +
					"azure-url=https://mrteamscaleshdev.file.core.windows.net/tstestshare/," +
					"azure-key=Ut0BQ2OEvgQXGnNJEjxnaEULAYgBpAK9+HukeKSzAB4CreIQkl2hikIbgNe4i+sL0uAbpTrFeFjOzh3bAtMMVg=="
		)
		Assertions.assertThat(agentOptions.azureFileStorageConfig.url.toString())
			.isEqualTo("https://mrteamscaleshdev.file.core.windows.net/tstestshare/")
		Assertions.assertThat(agentOptions.azureFileStorageConfig.accessKey).isEqualTo(
			"Ut0BQ2OEvgQXGnNJEjxnaEULAYgBpAK9+HukeKSzAB4CreIQkl2hikIbgNe4i+sL0uAbpTrFeFjOzh3bAtMMVg=="
		)
	}

	/** Tests the options for SAP NWDI applications.  */
	@Test
	@Throws(Exception::class)
	fun testValidSapNwdiOptions() {
		val agentOptions = parseAndMaybeThrow(
			"" +
					"teamscale-server-url=http://your.teamscale.url," +
					"teamscale-user=your-user-name," +
					"teamscale-access-token=your-access-token," +
					"teamscale-partition=Manual Tests," +
					"sap-nwdi-applications=com.example:project;com.test:p2"
		)
		Assertions.assertThat(agentOptions.sapNetWeaverJavaApplications)
			.isNotNull()
			.satisfies(ThrowingConsumer { sap ->
				Assertions.assertThat(sap).hasSize(2)
				Assertions.assertThat(sap).element(0).satisfies({ application ->
					Assertions.assertThat(application!!.markerClass).isEqualTo("com.example")
					Assertions.assertThat(application.teamscaleProject).isEqualTo("project")
				})
				Assertions.assertThat(sap).element(1).satisfies({ application ->
					Assertions.assertThat(application!!.markerClass).isEqualTo("com.test")
					Assertions.assertThat(application.teamscaleProject).isEqualTo("p2")
				})
			})
		Assertions.assertThat(agentOptions.teamscaleServer.isConfiguredForMultiProjectUpload).isTrue()
		Assertions.assertThat(agentOptions.teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload).isFalse()
	}

	/**
	 * Tests successful parsing of the [ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION]
	 */
	@Test
	@Throws(Exception::class)
	fun testArtifactoryApiKeyOptionIsCorrectlyParsed() {
		val someArtifactoryApiKey = "some_api_key"
		val agentOptions = parseAndMaybeThrow(
			String.format(
				"%s=%s,%s=%s,%s=%s", ArtifactoryConfig.ARTIFACTORY_URL_OPTION, "http://some_url",
				ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION, someArtifactoryApiKey,
				ArtifactoryConfig.ARTIFACTORY_PARTITION, "partition"
			)
		)
		Assertions.assertThat(agentOptions.artifactoryConfig.apiKey).isEqualTo(someArtifactoryApiKey)
	}

	/**
	 * Tests that setting [ArtifactoryConfig.ARTIFACTORY_USER_OPTION] and
	 * [ArtifactoryConfig.ARTIFACTORY_PASSWORD_OPTION] (along with
	 * [ArtifactoryConfig.ARTIFACTORY_URL_OPTION]) passes the AgentOptions' validity check.
	 */
	@Test
	@Throws(Exception::class)
	fun testArtifactoryBasicAuthSetPassesValidityCheck() {
		val agentOptions = parseAndMaybeThrow("")
		agentOptions.artifactoryConfig.url = "http://some_url".toHttpUrl()
		agentOptions.artifactoryConfig.user = "user"
		agentOptions.artifactoryConfig.password = "password"
		agentOptions.artifactoryConfig.partition = "partition"
		Assertions.assertThat(agentOptions.validator.isValid).isTrue()
	}

	/**
	 * Tests that setting [ArtifactoryConfig.ARTIFACTORY_USER_OPTION] and
	 * [ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION] (along with
	 * [ArtifactoryConfig.ARTIFACTORY_URL_OPTION]) passes the AgentOptions' validity check.
	 */
	@Test
	@Throws(Exception::class)
	fun testArtifactoryApiKeySetPassesValidityCheck() {
		val agentOptions = parseAndMaybeThrow("")
		agentOptions.artifactoryConfig.url = "http://some_url".toHttpUrl()
		agentOptions.artifactoryConfig.apiKey = "api_key"
		agentOptions.artifactoryConfig.partition = "partition"
		Assertions.assertThat(agentOptions.validator.isValid).isTrue()
	}

	/**
	 * Tests that the [TeamscaleProxyOptions] for HTTP are parsed correctly and correctly put into
	 * system properties that can be read using [TeamscaleProxySystemProperties].
	 */
	@Test
	@Throws(Exception::class)
	fun testTeamscaleProxyOptionsCorrectlySetSystemPropertiesForHttp() {
		testTeamscaleProxyOptionsCorrectlySetSystemProperties(ProxySystemProperties.Protocol.HTTP)
	}

	/**
	 * Tests that the [TeamscaleProxyOptions] for HTTPS are parsed correctly and correctly put into
	 * system properties that can be read using [TeamscaleProxySystemProperties].
	 */
	@Test
	@Throws(Exception::class)
	fun testTeamscaleProxyOptionsCorrectlySetSystemPropertiesForHttps() {
		testTeamscaleProxyOptionsCorrectlySetSystemProperties(ProxySystemProperties.Protocol.HTTPS)
	}

	/**
	 * Temporary folder to create the password file for
	 * [testTeamscaleProxyOptionsAreUsedWhileFetchingConfigFromTeamscale].
	 */
	@TempDir
	var temporaryDirectory: File? = null

	/**
	 * Test that the proxy settings are put into system properties and used for fetching a profiler configuration from
	 * Teamscale. Also tests that it is possible to specify the proxy password in a file and that this overwrites the
	 * password specified as agent option.
	 */
	@Test
	@Throws(Exception::class)
	fun testTeamscaleProxyOptionsAreUsedWhileFetchingConfigFromTeamscale() {
		val expectedUser = "user"
		// this is the password passed as agent property, it should be overwritten by the password file
		val unexpectedPassword = "not-my-password"

		val expectedPassword = "password"
		val passwordFile = writePasswortToPasswordFile(expectedPassword)

		MockWebServer().use { mockProxyServer ->
			val expectedHost = mockProxyServer.hostName
			val expectedPort = mockProxyServer.port


			val expectedProfilerConfiguration = ProfilerConfiguration()
			expectedProfilerConfiguration.configurationId = "config-id"
			expectedProfilerConfiguration.configurationOptions = "mode=testwise\ntia-mode=disk"
			val profilerRegistration = ProfilerRegistration()
			profilerRegistration.profilerId = "profiler-id"
			profilerRegistration.profilerConfiguration = expectedProfilerConfiguration

			mockProxyServer.enqueue(MockResponse().setResponseCode(407))
			mockProxyServer.enqueue(MockResponse().setResponseCode(200).setBody(profilerRegistration.serializeToJson()))

			val agentOptions = parseProxyOptions(
				"config-id=config,",
				ProxySystemProperties.Protocol.HTTP,
				expectedHost,
				expectedPort,
				expectedUser,
				unexpectedPassword,
				passwordFile
			)

			Assertions.assertThat(agentOptions.configurationViaTeamscale!!.profilerConfiguration!!.configurationId)
				.isEqualTo(expectedProfilerConfiguration.configurationId)
			Assertions.assertThat(agentOptions.mode).isEqualTo(EMode.TESTWISE)

			// 2 requests: one without proxy authentication, which failed (407), one with proxy authentication
			Assertions.assertThat(mockProxyServer.requestCount).isEqualTo(2)

			mockProxyServer.takeRequest()
			val requestWithProxyAuth = mockProxyServer.takeRequest() // this is the interesting request

			// check that the correct password was used
			val base64EncodedBasicAuth = Base64.getEncoder().encodeToString(
				("$expectedUser:$expectedPassword").toByteArray(
					StandardCharsets.UTF_8
				)
			)
			Assertions.assertThat(requestWithProxyAuth.getHeader(PROXY_AUTHORIZATION_HTTP_HEADER))
				.isEqualTo("Basic $base64EncodedBasicAuth")
		}
	}

	@Throws(IOException::class)
	private fun writePasswortToPasswordFile(expectedPassword: String): File {
		val passwordFile = File(temporaryDirectory, "password.txt")

		val bufferedWriter = BufferedWriter(FileWriter(passwordFile))
		bufferedWriter.write(expectedPassword)
		bufferedWriter.close()

		return passwordFile
	}

	@Throws(Exception::class)
	private fun testTeamscaleProxyOptionsCorrectlySetSystemProperties(protocol: ProxySystemProperties.Protocol) {
		val expectedHost = "host"
		val expectedPort = 9999
		val expectedUser = "user"
		val expectedPassword = "password"
		val agentOptions: AgentOptions = parseProxyOptions(
			"", protocol,
			expectedHost, expectedPort, expectedUser, expectedPassword, null
		)

		// clear to be sure the system properties are empty
		clearTeamscaleProxySystemProperties(protocol)

		putTeamscaleProxyOptionsIntoSystemProperties(agentOptions)

		assertTeamscaleProxySystemPropertiesAreCorrect(
			protocol,
			expectedHost,
			expectedPort,
			expectedUser,
			expectedPassword
		)

		clearTeamscaleProxySystemProperties(protocol)
	}

	@Throws(ProxySystemProperties.IncorrectPortFormatException::class)
	private fun assertTeamscaleProxySystemPropertiesAreCorrect(
		protocol: ProxySystemProperties.Protocol,
		expectedHost: String?,
		expectedPort: Int,
		expectedUser: String?,
		expectedPassword: String?
	) {
		val teamscaleProxySystemProperties = TeamscaleProxySystemProperties(protocol)
		Assertions.assertThat(teamscaleProxySystemProperties.proxyHost).isEqualTo(expectedHost)
		Assertions.assertThat(teamscaleProxySystemProperties.proxyPort).isEqualTo(expectedPort)
		Assertions.assertThat(teamscaleProxySystemProperties.proxyUser).isEqualTo(expectedUser)
		Assertions.assertThat(teamscaleProxySystemProperties.proxyPassword).isEqualTo(expectedPassword)
	}

	private fun clearTeamscaleProxySystemProperties(protocol: ProxySystemProperties.Protocol) {
		TeamscaleProxySystemProperties(protocol).clear()
	}

	@Throws(Exception::class)
	private fun parseAndMaybeThrow(options: String?): AgentOptions {
		val parser: AgentOptionsParser = agentOptionsParserWithDummyLogger
		val result = parser.parse(options)
		parser.throwOnCollectedErrors()
		return result
	}

	companion object {
		@Throws(Exception::class)
		private fun parseProxyOptions(
			otherOptionsString: String?, protocol: ProxySystemProperties.Protocol?,
			expectedHost: String?, expectedPort: Int, expectedUser: String?,
			expectedPassword: String?, passwordFile: File?
		): AgentOptions {
			val proxyHostOption = String.format("proxy-%s-host=%s", protocol, expectedHost)
			val proxyPortOption = String.format("proxy-%s-port=%d", protocol, expectedPort)
			val proxyUserOption = String.format("proxy-%s-user=%s", protocol, expectedUser)
			val proxyPasswordOption = String.format("proxy-%s-password=%s", protocol, expectedPassword)
			var optionsString = String.format(
				"%s%s,%s,%s,%s",
				otherOptionsString,
				proxyHostOption,
				proxyPortOption,
				proxyUserOption,
				proxyPasswordOption
			)

			if (passwordFile != null) {
				val proxyPasswordFileOption = String.format("proxy-password-file=%s", passwordFile.getAbsoluteFile())
				optionsString += ",$proxyPasswordFileOption"
			}

			val credentials = TeamscaleCredentials("http://localhost:80".toHttpUrlOrNull(), "unused", "unused")
			return getAgentOptionsParserWithDummyLoggerAndCredentials(credentials).parse(optionsString)
		}

		/** Returns the include filter predicate for the given filter expression.  */
		@Throws(Exception::class)
		private fun includeFilter(filterString: String?): Predicate<String?> {
			val agentOptions: AgentOptions = agentOptionsParserWithDummyLogger
				.parse("includes=$filterString")
			return Predicate { string: String? -> agentOptions.locationIncludeFilter.isIncluded(string!!) }
		}

		/** Returns the include filter predicate for the given filter expression.  */
		@Throws(Exception::class)
		private fun excludeFilter(filterString: String?): Predicate<String?> {
			val agentOptions: AgentOptions = agentOptionsParserWithDummyLogger
				.parse("excludes=$filterString")
			return Predicate { string: String? -> agentOptions.locationIncludeFilter.isIncluded(string!!) }
		}

		private val agentOptionsParserWithDummyLogger: AgentOptionsParser
			get() = AgentOptionsParser(CommandLineLogger(), null, null, null, null)

		private fun getAgentOptionsParserWithDummyLoggerAndCredentials(credentials: TeamscaleCredentials?): AgentOptionsParser {
			return AgentOptionsParser(CommandLineLogger(), null, null, credentials, null)
		}

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
