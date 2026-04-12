package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.ClusteredTestDetails;
import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.EReportFormat;
import com.teamscale.client.PrioritizableTest;
import com.teamscale.client.PrioritizableTestCluster;
import com.teamscale.client.TeamscaleClient;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.jacoco.agent.options.ETestwiseCoverageMode;
import com.teamscale.jacoco.agent.util.TestUtils;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;
import com.teamscale.report.testwise.model.ETestExecutionResult;
import com.teamscale.tia.client.RunningTest;
import com.teamscale.tia.client.TestRun;
import com.teamscale.tia.client.TestRunWithClusteredSuggestions;
import com.teamscale.tia.client.TiaAgent;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TestwiseCoverageAgentTest {
	private static final String FORBIDDEN_MESSAGE_PREFIX = "HTTP Status Code: 403 Forbidden\nMessage: ";
	private static final String MISSING_VIEW_PERMISSIONS = "User doesn't have permission 'VIEW' on project x.";
	private static final MediaType PLAIN_TEXT = MediaType.parse("plain/text");

	@Mock
	private TeamscaleClient client;

	@Mock
	private JaCoCoTestwiseReportGenerator reportGenerator;

	@TempDir
	File tempDir;

	@Test
	public void testAccessViaTiaClientAndReportUploadToTeamscale() throws Exception {
		List<ClusteredTestDetails> availableTests = Arrays
				.asList(new ClusteredTestDetails("test1", "test1", "content", "cluster"),
						new ClusteredTestDetails("test2", "test2", "content", "cluster"));
		List<PrioritizableTestCluster> impactedClusters = Collections
				.singletonList(new PrioritizableTestCluster("cluster",
						Collections.singletonList(new PrioritizableTest("test2"))));
		when(client.getImpactedTests(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
				.thenReturn(Response.success(impactedClusters));

		when(reportGenerator.convert(any(File.class)))
				.thenReturn(CoverageToTeamscaleStrategyTest.getDummyTestwiseCoverage("test2"));

		int port;
		synchronized (TestUtils.class) {
			port = TestUtils.getFreePort();
			AgentOptions options = mockOptions(port);
			when(options.createNewFileInOutputDirectory(any(), any())).thenReturn(new File(tempDir, "test"));
			new TestwiseCoverageAgent(options, null, reportGenerator);
		}

		TiaAgent agent = new TiaAgent(false, HttpUrl.get("http://localhost:" + port));

		TestRunWithClusteredSuggestions testRun = agent.startTestRun(availableTests);
		assertThat(testRun.prioritizedClusters).hasSize(1);
		assertThat(testRun.prioritizedClusters.get(0).tests).hasSize(1);
		PrioritizableTest test = testRun.prioritizedClusters.get(0).tests.get(0);
		assertThat(test.testName).isEqualTo("test2");

		RunningTest runningTest = testRun.startTest(test.testName);
		runningTest.endTest(new TestRun.TestResultWithMessage(ETestExecutionResult.PASSED, "message"));

		testRun.endTestRun(true);
		verify(client).uploadReport(eq(EReportFormat.TESTWISE_COVERAGE),
				matches("\\Q{\"partial\":true,\"tests\":[{\"uniformPath\":\"test1\",\"sourcePath\":\"test1\",\"content\":\"content\",\"paths\":[]},{\"uniformPath\":\"test2\",\"sourcePath\":\"test2\",\"content\":\"content\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"message\":\"message\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
				any(), any(), any(), any(), any());
	}

	@Test
	public void testErrorHandling() throws Exception {
		when(client.getImpactedTests(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
				.thenReturn(Response.error(403, ResponseBody.create(FORBIDDEN_MESSAGE_PREFIX + MISSING_VIEW_PERMISSIONS,
						PLAIN_TEXT)));

		int port;
		synchronized (TestUtils.class) {
			port = TestUtils.getFreePort();
			AgentOptions options = mockOptions(port);
			new TestwiseCoverageAgent(options, null, reportGenerator);
		}

		TiaAgent agent = new TiaAgent(false, HttpUrl.get("http://localhost:" + port));
		assertThatCode(agent::startTestRunAssumingUnchangedTests).hasMessageContaining(MISSING_VIEW_PERMISSIONS);
	}

	private interface ITestwiseCoverageAgentApiWithoutBody {

		/**
		 * Version of testrun/start that doesn't have a body. This can't be triggered via the Java TIA client but is a
		 * supported version of the API for other clients.
		 */
		@POST("testrun/start")
		Call<List<PrioritizableTestCluster>> testRunStarted(
				@Query("include-non-impacted") boolean includeNonImpacted,
				@Query("baseline") Long baseline
		);

	}

	@Test
	public void shouldHandleMissingRequestBodyForTestrunStartGracefully() throws Exception {
		List<PrioritizableTestCluster> impactedClusters = Collections
				.singletonList(new PrioritizableTestCluster("cluster",
						Collections.singletonList(new PrioritizableTest("test2"))));
		when(client.getImpactedTests(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
				.thenReturn(Response.success(impactedClusters));

		int port;
		synchronized (TestUtils.class) {
			port = TestUtils.getFreePort();
			new TestwiseCoverageAgent(mockOptions(port), null, reportGenerator);
		}

		ITestwiseCoverageAgentApiWithoutBody api = new Retrofit.Builder()
				.addConverterFactory(JacksonConverterFactory.create())
				.baseUrl("http://localhost:" + port)
				.build().create(ITestwiseCoverageAgentApiWithoutBody.class);
		Response<List<PrioritizableTestCluster>> response = api.testRunStarted(false, null).execute();

		assertThat(response.isSuccessful()).describedAs(response.toString()).isTrue();
		List<PrioritizableTestCluster> tests = response.body();
		assertThat(tests).isNotNull().hasSize(1);
		assertThat(tests.get(0).tests).hasSize(1);
	}

	private AgentOptions mockOptions(int port) {
		AgentOptions options = mock(AgentOptions.class);
		when(options.createTeamscaleClient(true)).thenReturn(client);

		TeamscaleServer server = new TeamscaleServer();
		server.commit = new CommitDescriptor("branch", "12345");
		server.url = HttpUrl.get("https://doesnt-exist.io");
		server.userName = "build";
		server.userAccessToken = "token";
		server.partition = "partition";
		options.teamscaleServer = server;
		options.httpServerPort = port;
		options.testwiseCoverageMode = ETestwiseCoverageMode.TEAMSCALE_UPLOAD;

		when(options.createTeamscaleClient(true)).thenReturn(client);
		return options;
	}
}
