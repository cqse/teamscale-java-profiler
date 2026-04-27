package com.teamscale.jacoco.agent.options;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.JsonUtils;
import com.teamscale.client.ProfilerConfiguration;
import com.teamscale.client.ProfilerRegistration;
import com.teamscale.client.ProxySystemProperties;
import com.teamscale.client.TeamscaleProxySystemProperties;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig;
import com.teamscale.jacoco.agent.util.TestUtils;
import com.teamscale.report.util.CommandLineLogger;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Predicate;

import static com.teamscale.client.HttpUtils.PROXY_AUTHORIZATION_HTTP_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the {@link AgentOptions}. */
public class AgentOptionsTest {

	@TempDir
	public File testFolder;

	@SuppressWarnings("ResultOfMethodCallIgnored")
	@BeforeEach
	public void setUp() throws IOException {
		new File(testFolder, "file_with_manifest1.jar").createNewFile();
		new File(testFolder, "plugins/inner").mkdirs();
		new File(testFolder, "plugins/some_other_file.jar").createNewFile();
		new File(testFolder, "plugins/file_with_manifest2.jar").createNewFile();
	}

	/** Tests include pattern matching. */
	@Test
	public void testIncludePatternMatching() throws Exception {
		assertThat(includeFilter("com.*")).accepts("file.jar@com/foo/Bar.class", "file.jar@com/foo/Bar$Goo.class",
				"file1.jar@goo/file2.jar@com/foo/Bar.class", "com/foo/Bar.class", "com.foo/Bar.class");
		assertThat(includeFilter("com.*")).rejects("foo/com/Bar.class", "com.class", "file.jar@com.class",
				"A$com$Bar.class");
		assertThat(includeFilter("*com.*")).accepts("file.jar@com/foo/Bar.class", "file.jar@com/foo/Bar$Goo.class",
				"file1.jar@goo/file2.jar@com/foo/Bar.class", "com/foo/Bar.class", "foo/com/goo/Bar.class",
				"A$com$Bar.class", "src/com/foo/Bar.class");
		assertThat(includeFilter("*com.*;*de.*"))
				.accepts("file.jar@com/foo/Bar.class", "file.jar@de/foo/Bar$Goo.class");
		assertThat(excludeFilter("*com.*;*de.*"))
				.rejects("file.jar@com/foo/Bar.class", "file.jar@de/foo/Bar$Goo.class");
		assertThat(includeFilter("*com.customer.*")).accepts(
				"C:\\client-daily\\client\\plugins\\com.customer.something.client_1.2.3.4.1234566778.jar@com/customer/something/SomeClass.class");
	}

	/** Interval options test. */
	@Test
	public void testIntervalOptions() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("");
		assertThat(agentOptions.getDumpIntervalInMinutes()).isEqualTo(480);
		agentOptions = parseAndMaybeThrow("interval=0");
		assertThat(agentOptions.shouldDumpInIntervals()).isEqualTo(false);
		agentOptions = parseAndMaybeThrow("interval=30");
		assertThat(agentOptions.shouldDumpInIntervals()).isEqualTo(true);
		assertThat(agentOptions.getDumpIntervalInMinutes()).isEqualTo(30);
	}

	/** Tests the options for uploading coverage to teamscale. */
	@Test
	public void testTeamscaleUploadOptions() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("" +
				"teamscale-server-url=127.0.0.1," +
				"teamscale-project=test," +
				"teamscale-user=build," +
				"teamscale-access-token=token," +
				"teamscale-partition=\"Unit Tests\"," +
				"teamscale-commit=default:HEAD," +
				"teamscale-message=\"This is my message\"");

		TeamscaleServer teamscaleServer = agentOptions.getTeamscaleServerOptions();
		assertThat(teamscaleServer.url.toString()).isEqualTo("http://127.0.0.1/");
		assertThat(teamscaleServer.project).isEqualTo("test");
		assertThat(teamscaleServer.userName).isEqualTo("build");
		assertThat(teamscaleServer.userAccessToken).isEqualTo("token");
		assertThat(teamscaleServer.partition).isEqualTo("Unit Tests");
		assertThat(teamscaleServer.commit.toString()).isEqualTo("default:HEAD");
		assertThat(teamscaleServer.getMessage()).isEqualTo("This is my message");
	}

	/** Tests the options for the Test Impact mode. */
	@Test
	public void testHttpServerOptions() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("mode=TESTWISE,class-dir=.," +
				"http-server-port=8081");
		assertThat(agentOptions.getHttpServerPort()).isEqualTo(8081);
	}

	/** Tests the options http-server-port option for normal mode. */
	@Test
	public void testHttpServerOptionsForNormalMode() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("http-server-port=8081");
		assertThat(agentOptions.getHttpServerPort()).isEqualTo(8081);
	}

	/** Tests the options for the Test Impact mode. */
	@Test
	public void testHttpServerOptionsWithCoverageViaHttp() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("mode=TESTWISE,class-dir=.," +
				"http-server-port=8081,tia-mode=http");
		assertThat(agentOptions.getHttpServerPort()).isEqualTo(8081);
		assertThat(agentOptions.getTestwiseCoverageMode()).isEqualTo(ETestwiseCoverageMode.HTTP);
	}

	/** Tests setting ignore-uncovered-classes works. */
	@Test
	public void testIgnoreUncoveredClasses() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("ignore-uncovered-classes=true");
		assertTrue(agentOptions.shouldIgnoreUncoveredClasses());
	}

	/** Tests default for ignore-uncovered-classes is false. */
	@Test
	public void testIgnoreUncoveredClassesDefault() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("");
		assertFalse(agentOptions.shouldIgnoreUncoveredClasses());
	}

	/** Tests default for ignore-uncovered-classes is false. */
	@Test
	public void shouldAllowMinusForEnumConstants() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("tia-mode=exec-file");
		assertThat(agentOptions.getTestwiseCoverageMode()).isEqualTo(ETestwiseCoverageMode.EXEC_FILE);
	}

	/** Tests that supplying both revision and commit info is forbidden. */
	@Test
	public void testBothRevisionAndCommitSupplied() throws URISyntaxException {
		String message = "'teamscale-revision' and 'teamscale-revision-manifest-jar' are incompatible with "
				+ "'teamscale-commit' and 'teamscale-commit-manifest-jar'.";

		File jar = new File(getClass().getResource("manifest-and-git-properties.jar").toURI());

		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-revision=1234,teamscale-commit=master:1000"))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-revision=1234,teamscale-commit-manifest-jar=" + jar.getAbsolutePath()))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-revision-manifest-jar=" + jar.getAbsolutePath() + ",teamscale-commit=master:1000"))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-revision-manifest-jar=" + jar.getAbsolutePath() + ",teamscale-commit-manifest-jar=" + jar.getAbsolutePath()))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
	}

	/** Tests the 'teamscale-revision-manifest-jar' option correctly parses the 'Git_Commit' field in the manifest. */
	@Test
	public void testTeamscaleRevisionManifestJarOption() throws Exception {
		File jar = new File(getClass().getResource("manifest-with-git-commit-revision.jar").toURI());
		AgentOptions options = parseAndMaybeThrow(
				"teamscale-revision-manifest-jar=" + jar.getAbsolutePath() + ",teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p");

		assertThat(options.getTeamscaleServerOptions().revision).isEqualTo("f364d58dc4966ca856260185e46a90f80ee5e9c6");
	}

	/**
	 * Tests that an exception is thrown when the user attempts to specify the commit/revision when 'teamscale-project'
	 * is not specified. The absence of the `teamscale-project` implies a multi-project upload, so the commit/revision
	 * have to be provided individually via the 'git.properties' file.
	 */
	@Test
	public void testNoCommitOrRevisionGivenWhenProjectNull() throws Exception {
		String message = "You tried to provide a commit to upload to directly." +
				" This is not possible, since you did not provide the 'teamscale-project' to upload to";

		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-server-url=127.0.0.1," +
								"teamscale-user=build," +
								"teamscale-access-token=token," +
								"teamscale-partition=\"Unit Tests\"," +
								"teamscale-commit=default:HEAD," +
								"teamscale-message=\"This is my message\""))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"teamscale-server-url=127.0.0.1," +
								"teamscale-user=build," +
								"teamscale-access-token=token," +
								"teamscale-partition=\"Unit Tests\"," +
								"teamscale-revision=1234ABCD," +
								"teamscale-message=\"This is my message\""))
				.isInstanceOf(AgentOptionParseException.class).hasMessageContaining(message);
	}

	/**
	 * Test that agent continues to run if the user provided an invalid path via the
	 * {@link AgentOptions#GIT_PROPERTIES_JAR_OPTION} jar option.
	 */
	@Test
	public void testGitPropertiesJarOptionWithNonExistentFileDoesNotFailBadly() throws Exception {
		File jarFile = new File(getClass().getResource("nested-jar.war").getFile());
		AgentOptions agentOptions = parseAndMaybeThrow(
				AgentOptions.GIT_PROPERTIES_JAR_OPTION + "=doesNotExist" + File.separator + jarFile.getAbsolutePath());
		assertThat(agentOptions.gitPropertiesJar).isNull();
	}

	/** Test that the {@link AgentOptions#GIT_PROPERTIES_JAR_OPTION} option can be parsed correctly */
	@Test
	public void testGitPropertiesJarOptionParsedCorrectly() throws Exception {
		File jarFile = new File(getClass().getResource("nested-jar.war").getFile());
		AgentOptions agentOptions = parseAndMaybeThrow(
				AgentOptions.GIT_PROPERTIES_JAR_OPTION + "=" + jarFile.getAbsolutePath());
		assertThat(agentOptions.gitPropertiesJar).isNotNull();
	}

	/**
	 * Test that agent continues to run if the user provided a folder via the
	 * {@link AgentOptions#GIT_PROPERTIES_JAR_OPTION} jar option.
	 */
	@Test
	public void testGitPropertiesJarDoesNotAcceptFolders() throws Exception {
		File jarFile = new File(getClass().getResource("nested-jar.war").getFile());
		AgentOptions agentOptions = parseAndMaybeThrow(
				AgentOptions.GIT_PROPERTIES_JAR_OPTION + "=" + jarFile.getParent());
		assertThat(agentOptions.gitPropertiesJar).isNull();
	}

	/** Tests that supplying version info is supported in Testwise mode. */
	@Test
	public void testVersionInfosInTestwiseMode() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("mode=TESTWISE,class-dir=.," +
				"http-server-port=8081,teamscale-revision=1234,teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p");
		assertThat(agentOptions.getTeamscaleServerOptions().revision).isEqualTo("1234");

		agentOptions = parseAndMaybeThrow("mode=TESTWISE,class-dir=.," +
				"http-server-port=8081,teamscale-commit=branch:1234,teamscale-server-url=ts.com,teamscale-user=u,teamscale-access-token=t,teamscale-project=p,teamscale-partition=p");
		assertThat(agentOptions.getTeamscaleServerOptions().commit).isEqualTo(CommitDescriptor.parse("branch:1234"));
	}

	/** Tests the options for azure file storage upload. */
	@Test
	public void testAzureFileStorageOptions() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("" +
				"azure-url=https://mrteamscaleshdev.file.core.windows.net/tstestshare/," +
				"azure-key=Ut0BQ2OEvgQXGnNJEjxnaEULAYgBpAK9+HukeKSzAB4CreIQkl2hikIbgNe4i+sL0uAbpTrFeFjOzh3bAtMMVg==");
		assertThat(agentOptions.azureFileStorageConfig.url.toString())
				.isEqualTo("https://mrteamscaleshdev.file.core.windows.net/tstestshare/");
		assertThat(agentOptions.azureFileStorageConfig.accessKey).isEqualTo(
				"Ut0BQ2OEvgQXGnNJEjxnaEULAYgBpAK9+HukeKSzAB4CreIQkl2hikIbgNe4i+sL0uAbpTrFeFjOzh3bAtMMVg==");
	}

	/** Tests the options for SAP NWDI applications. */
	@Test
	public void testValidSapNwdiOptions() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("" +
				"teamscale-server-url=http://your.teamscale.url," +
				"teamscale-user=your-user-name," +
				"teamscale-access-token=your-access-token," +
				"teamscale-partition=Manual Tests," +
				"sap-nwdi-applications=com.example:project;com.test:p2");
		assertThat(agentOptions.sapNetWeaverJavaApplications)
				.isNotNull()
				.satisfies(sap -> {
					assertThat(sap).hasSize(2);
					assertThat(sap).element(0).satisfies(application -> {
						assertThat(application.markerClass).isEqualTo("com.example");
						assertThat(application.teamscaleProject).isEqualTo("project");
					});
					assertThat(sap).element(1).satisfies(application -> {
						assertThat(application.markerClass).isEqualTo("com.test");
						assertThat(application.teamscaleProject).isEqualTo("p2");
					});
				});
		assertThat(agentOptions.teamscaleServer.isConfiguredForMultiProjectUpload()).isTrue();
		assertThat(agentOptions.teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload()).isFalse();
	}

	/**
	 * Tests successful parsing of the {@link ArtifactoryConfig#ARTIFACTORY_API_KEY_OPTION}
	 */
	@Test
	public void testArtifactoryApiKeyOptionIsCorrectlyParsed() throws Exception {
		String someArtifactoryApiKey = "some_api_key";
		AgentOptions agentOptions = parseAndMaybeThrow(
				String.format("%s=%s,%s=%s,%s=%s", ArtifactoryConfig.ARTIFACTORY_URL_OPTION, "http://some_url",
						ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION, someArtifactoryApiKey,
						ArtifactoryConfig.ARTIFACTORY_PARTITION, "partition"));
		assertThat(agentOptions.artifactoryConfig.apiKey).isEqualTo(someArtifactoryApiKey);
	}

	/**
	 * Tests that setting {@link ArtifactoryConfig#ARTIFACTORY_USER_OPTION} and
	 * {@link ArtifactoryConfig#ARTIFACTORY_PASSWORD_OPTION} (along with
	 * {@link ArtifactoryConfig#ARTIFACTORY_URL_OPTION}) passes the AgentOptions' validity check.
	 */
	@Test
	public void testArtifactoryBasicAuthSetPassesValidityCheck() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("");
		agentOptions.artifactoryConfig.url = HttpUrl.get("http://some_url");
		agentOptions.artifactoryConfig.user = "user";
		agentOptions.artifactoryConfig.password = "password";
		agentOptions.artifactoryConfig.partition = "partition";
		assertThat(agentOptions.getValidator().isValid()).isTrue();
	}

	/**
	 * Tests that setting {@link ArtifactoryConfig#ARTIFACTORY_USER_OPTION} and
	 * {@link ArtifactoryConfig#ARTIFACTORY_API_KEY_OPTION} (along with
	 * {@link ArtifactoryConfig#ARTIFACTORY_URL_OPTION}) passes the AgentOptions' validity check.
	 */
	@Test
	public void testArtifactoryApiKeySetPassesValidityCheck() throws Exception {
		AgentOptions agentOptions = parseAndMaybeThrow("");
		agentOptions.artifactoryConfig.url = HttpUrl.get("http://some_url");
		agentOptions.artifactoryConfig.apiKey = "api_key";
		agentOptions.artifactoryConfig.partition = "partition";
		assertThat(agentOptions.getValidator().isValid()).isTrue();
	}

	/**
	 * Tests that the {@link TeamscaleProxyOptions} for HTTP are parsed correctly and correctly put into
	 * system properties that can be read using {@link TeamscaleProxySystemProperties}.
	 */
	@Test
	public void testTeamscaleProxyOptionsCorrectlySetSystemPropertiesForHttp() throws Exception {
		testTeamscaleProxyOptionsCorrectlySetSystemProperties(ProxySystemProperties.Protocol.HTTP);
	}

	/**
	 * Tests that the {@link TeamscaleProxyOptions} for HTTPS are parsed correctly and correctly put into
	 * system properties that can be read using {@link TeamscaleProxySystemProperties}.
	 */
	@Test
	public void testTeamscaleProxyOptionsCorrectlySetSystemPropertiesForHttps() throws Exception {
		testTeamscaleProxyOptionsCorrectlySetSystemProperties(ProxySystemProperties.Protocol.HTTPS);
	}

	/**
	 * Temporary folder to create the password file for
	 * {@link AgentOptionsTest#testTeamscaleProxyOptionsAreUsedWhileFetchingConfigFromTeamscale()}.
	 */
	@TempDir
	public File temporaryDirectory;

	/**
	 * Test that the proxy settings are put into system properties and used for fetching a profiler configuration from
	 * Teamscale. Also tests that it is possible to specify the proxy password in a file and that this overwrites the
	 * password specified as agent option.
	 */
	@Test
	public void testTeamscaleProxyOptionsAreUsedWhileFetchingConfigFromTeamscale() throws Exception {
		String expectedUser = "user";
		// this is the password passed as agent property, it should be overwritten by the password file
		String unexpectedPassword = "not-my-password";

		String expectedPassword = "password";
		File passwordFile = writePasswortToPasswordFile(expectedPassword);

		try (MockWebServer mockProxyServer = new MockWebServer()) {
			String expectedHost = mockProxyServer.getHostName();
			int expectedPort = mockProxyServer.getPort();


			ProfilerConfiguration expectedProfilerConfiguration = new ProfilerConfiguration();
			expectedProfilerConfiguration.configurationId = "config-id";
			expectedProfilerConfiguration.configurationOptions = "mode=testwise\ntia-mode=disk";
			ProfilerRegistration profilerRegistration = new ProfilerRegistration();
			profilerRegistration.profilerId = "profiler-id";
			profilerRegistration.profilerConfiguration = expectedProfilerConfiguration;

			mockProxyServer.enqueue(new MockResponse().setResponseCode(407));
			mockProxyServer.enqueue(new MockResponse().setResponseCode(200).setBody(JsonUtils.serializeToJson(profilerRegistration)));

			AgentOptions agentOptions= parseProxyOptions("config-id=config,", ProxySystemProperties.Protocol.HTTP, expectedHost, expectedPort, expectedUser, unexpectedPassword, passwordFile);

			assertThat(agentOptions.configurationViaTeamscale.getProfilerConfiguration().configurationId).isEqualTo(expectedProfilerConfiguration.configurationId);
			assertThat(agentOptions.mode).isEqualTo(EMode.TESTWISE);

			// 2 requests: one without proxy authentication, which failed (407), one with proxy authentication
			assertThat(mockProxyServer.getRequestCount()).isEqualTo(2);

			mockProxyServer.takeRequest();
			RecordedRequest requestWithProxyAuth = mockProxyServer.takeRequest(); // this is the interesting request

			// check that the correct password was used
			String base64EncodedBasicAuth = Base64.getEncoder().encodeToString((expectedUser + ":" + expectedPassword).getBytes(
					StandardCharsets.UTF_8));
			assertThat(requestWithProxyAuth.getHeader(PROXY_AUTHORIZATION_HTTP_HEADER)).isEqualTo("Basic " + base64EncodedBasicAuth);
		}

	}

	private File writePasswortToPasswordFile(String expectedPassword) throws IOException {
		File passwordFile = new File(temporaryDirectory, "password.txt");

		BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(passwordFile));
		bufferedWriter.write(expectedPassword);
		bufferedWriter.close();

		return passwordFile;
	}

	private void testTeamscaleProxyOptionsCorrectlySetSystemProperties(ProxySystemProperties.Protocol protocol) throws Exception {
		String expectedHost = "host";
		int expectedPort = 9999;
		String expectedUser = "user";
		String expectedPassword = "password";
		AgentOptions agentOptions = parseProxyOptions("", protocol,
				expectedHost, expectedPort, expectedUser, expectedPassword, null);

		// clear to be sure the system properties are empty
		clearTeamscaleProxySystemProperties(protocol);

		AgentOptionsParser.putTeamscaleProxyOptionsIntoSystemProperties(agentOptions);

		assertTeamscaleProxySystemPropertiesAreCorrect(protocol, expectedHost, expectedPort, expectedUser, expectedPassword);

		clearTeamscaleProxySystemProperties(protocol);
	}

	private static AgentOptions parseProxyOptions(String otherOptionsString, ProxySystemProperties.Protocol protocol,
			String expectedHost, int expectedPort, String expectedUser,
			String expectedPassword, File passwordFile) throws Exception {
		String proxyHostOption = String.format("proxy-%s-host=%s", protocol, expectedHost);
		String proxyPortOption = String.format("proxy-%s-port=%d", protocol, expectedPort);
		String proxyUserOption = String.format("proxy-%s-user=%s", protocol, expectedUser);
		String proxyPasswordOption = String.format("proxy-%s-password=%s", protocol, expectedPassword);
		String optionsString = String.format("%s%s,%s,%s,%s", otherOptionsString, proxyHostOption, proxyPortOption, proxyUserOption, proxyPasswordOption);

		if (passwordFile != null) {
			String proxyPasswordFileOption = String.format("proxy-password-file=%s", passwordFile.getAbsoluteFile());
			optionsString += "," + proxyPasswordFileOption;
		}

		TeamscaleCredentials credentials = new TeamscaleCredentials(HttpUrl.parse("http://localhost:80"), "unused", "unused");
		return getAgentOptionsParserWithDummyLoggerAndCredentials(credentials).parse(optionsString);
	}

	private void assertTeamscaleProxySystemPropertiesAreCorrect(ProxySystemProperties.Protocol protocol, String expectedHost, int expectedPort, String expectedUser, String expectedPassword) throws ProxySystemProperties.IncorrectPortFormatException {
		TeamscaleProxySystemProperties teamscaleProxySystemProperties = new TeamscaleProxySystemProperties(protocol);
		assertThat(teamscaleProxySystemProperties.getProxyHost()).isEqualTo(expectedHost);
		assertThat(teamscaleProxySystemProperties.getProxyPort()).isEqualTo(expectedPort);
		assertThat(teamscaleProxySystemProperties.getProxyUser()).isEqualTo(expectedUser);
		assertThat(teamscaleProxySystemProperties.getProxyPassword()).isEqualTo(expectedPassword);
	}

	private void clearTeamscaleProxySystemProperties(ProxySystemProperties.Protocol protocol) {
		new TeamscaleProxySystemProperties(protocol).clear();
	}
	/** Returns the include filter predicate for the given filter expression. */
	private static Predicate<String> includeFilter(String filterString) throws Exception {
		AgentOptions agentOptions = getAgentOptionsParserWithDummyLogger()
				.parse("includes=" + filterString);
		return string -> agentOptions.getLocationIncludeFilter().isIncluded(string);
	}

	/** Returns the include filter predicate for the given filter expression. */
	private static Predicate<String> excludeFilter(String filterString) throws Exception {
		AgentOptions agentOptions = getAgentOptionsParserWithDummyLogger()
				.parse("excludes=" + filterString);
		return string -> agentOptions.getLocationIncludeFilter().isIncluded(string);
	}

	private static AgentOptionsParser getAgentOptionsParserWithDummyLogger() {
		return new AgentOptionsParser(new CommandLineLogger(), null, null, null, null);
	}

	private static AgentOptionsParser getAgentOptionsParserWithDummyLoggerAndCredentials(TeamscaleCredentials credentials) {
		return new AgentOptionsParser(new CommandLineLogger(), null, null, credentials, null);
	}

	/** Tests that config-id without teamscale-user reports the missing option by name. */
	@Test
	public void configIdWithMissingUserReportsMissingOption() {
		assertThatThrownBy(
				() -> parseAndMaybeThrow(
						"config-id=test,teamscale-server-url=http://localhost:8080,teamscale-access-token=keyfoo"))
				.isInstanceOf(AgentOptionParseException.class)
				.hasMessageContaining("teamscale-user");
	}

	/** Tests that config-id with all required options missing reports all of them. */
	@Test
	public void configIdWithAllMissingOptionsReportsAll() {
		assertThatThrownBy(
				() -> parseAndMaybeThrow("config-id=test"))
				.isInstanceOf(AgentOptionParseException.class)
				.hasMessageContaining("teamscale-server-url")
				.hasMessageContaining("teamscale-user")
				.hasMessageContaining("teamscale-access-token");
	}

	/**
	 * Delete created coverage folders
	 */
	@AfterAll
	public static void teardown() throws IOException {
		TestUtils.cleanAgentCoverageDirectory();
	}

	private AgentOptions parseAndMaybeThrow(String options) throws Exception {
		AgentOptionsParser parser = getAgentOptionsParserWithDummyLogger();
		AgentOptions result = parser.parse(options);
		parser.throwOnCollectedErrors();
		return result;
	}
}
