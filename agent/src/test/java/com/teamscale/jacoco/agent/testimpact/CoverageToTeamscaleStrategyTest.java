package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.ClusteredTestDetails;
import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.EReportFormat;
import com.teamscale.client.PrioritizableTest;
import com.teamscale.client.PrioritizableTestCluster;
import com.teamscale.client.TeamscaleClient;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.JacocoRuntimeController;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;
import com.teamscale.report.testwise.model.ETestExecutionResult;
import com.teamscale.report.testwise.model.TestExecution;
import com.teamscale.report.testwise.model.TestwiseCoverage;
import com.teamscale.report.testwise.model.builder.FileCoverageBuilder;
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CoverageToTeamscaleStrategyTest {

	@Mock
	private TeamscaleClient client;

	@Mock
	private JaCoCoTestwiseReportGenerator reportGenerator;

	@Mock
	private JacocoRuntimeController controller;

	@TempDir
	File tempDir;

	@Test
	public void shouldRecordCoverageForTestsEvenIfNotProvidedAsAvailableTest() throws Exception {
		AgentOptions options = mockOptions(false);
		CoverageToTeamscaleStrategy strategy = new CoverageToTeamscaleStrategy(controller, options, reportGenerator);

		TestwiseCoverage testwiseCoverage = getDummyTestwiseCoverage("mytest");
		when(reportGenerator.convert(any(File.class))).thenReturn(testwiseCoverage);

		// we skip testRunStart and don't provide any available tests
		strategy.testStart("mytest");
		strategy.testEnd("mytest", new TestExecution("mytest", 0L, ETestExecutionResult.PASSED));
		strategy.testRunEnd(false);

		verify(client).uploadReport(eq(EReportFormat.TESTWISE_COVERAGE),
				matches("\\Q{\"partial\":false,\"tests\":[{\"uniformPath\":\"mytest\",\"sourcePath\":\"mytest\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
				any(), any(), any(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testValidCallSequence(boolean useRevision) throws Exception {
		List<PrioritizableTestCluster> clusters = Collections
				.singletonList(new PrioritizableTestCluster("cluster",
						Collections.singletonList(new PrioritizableTest("mytest"))));
		when(client.getImpactedTests(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
				anyBoolean())).thenReturn(
				Response.success(clusters));

		TestwiseCoverage testwiseCoverage = getDummyTestwiseCoverage("mytest");
		when(reportGenerator.convert(any(File.class))).thenReturn(testwiseCoverage);

		AgentOptions options = mockOptions(useRevision);
		JacocoRuntimeController controller = mock(JacocoRuntimeController.class);
		CoverageToTeamscaleStrategy strategy = new CoverageToTeamscaleStrategy(controller, options, reportGenerator);

		strategy.testRunStart(
				Collections.singletonList(
						new ClusteredTestDetails("mytest", "mytest", "content", "cluster")), false,
				true, true,
				null, null);
		strategy.testStart("mytest");
		strategy.testEnd("mytest", new TestExecution("mytest", 0L, ETestExecutionResult.PASSED));
		strategy.testRunEnd(true);

		verify(client).uploadReport(eq(EReportFormat.TESTWISE_COVERAGE),
				matches("\\Q{\"partial\":true,\"tests\":[{\"uniformPath\":\"mytest\",\"sourcePath\":\"mytest\",\"content\":\"content\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
				any(), any(), any(), any(), any());
	}

	/** Returns a dummy testwise coverage object for a test with the given name that covers a few lines of Main.java. */
	protected static TestwiseCoverage getDummyTestwiseCoverage(String test) {
		TestCoverageBuilder testCoverageBuilder = new TestCoverageBuilder(test);
		FileCoverageBuilder fileCoverageBuilder = new FileCoverageBuilder("src/main/java", "Main.java");
		fileCoverageBuilder.addLineRange(1, 4);
		testCoverageBuilder.add(fileCoverageBuilder);
		TestwiseCoverage testwiseCoverage = new TestwiseCoverage();
		testwiseCoverage.add(testCoverageBuilder);
		return testwiseCoverage;
	}

	private AgentOptions mockOptions(boolean useRevision) throws IOException {
		AgentOptions options = mock(AgentOptions.class);
		when(options.createTeamscaleClient(true)).thenReturn(client);
		when(options.createNewFileInOutputDirectory(any(), any())).thenReturn(new File(tempDir, "test"));

		TeamscaleServer server = new TeamscaleServer();
		if (useRevision) {
			server.revision = "rev1";
		} else {
			server.commit = new CommitDescriptor("branch", "12345");
		}
		server.url = HttpUrl.get("https://doesnt-exist.io");
		server.userName = "build";
		server.userAccessToken = "token";
		server.partition = "partition";
		options.teamscaleServer = server;

		when(options.createTeamscaleClient(true)).thenReturn(client);
		return options;
	}

}
