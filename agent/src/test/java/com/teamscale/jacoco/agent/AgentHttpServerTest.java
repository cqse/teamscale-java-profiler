package com.teamscale.jacoco.agent;

import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.jacoco.agent.options.TestAgentOptionsBuilder;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentHttpServerTest {

	private Agent agent;
	private final URI baseUri;
	private final Integer httpServerPort = 8081;
	private final String defaultCommitMessage = "Some Message";
	private final String defaultPartition = "Some Partition";

	public AgentHttpServerTest() throws URISyntaxException {
		baseUri = new URI("http://localhost:" + httpServerPort);
	}

	/** Starts the http server to control the agent */
	@BeforeEach
	public void setup() throws Exception {
		AgentOptions options = new TestAgentOptionsBuilder()
				.withHttpServerPort(httpServerPort)
				.withTeamscaleMessage(defaultCommitMessage)
				.withTeamscalePartition(defaultPartition)
				.create();

		agent = new Agent(options, null);
	}

	/** Stops the http server */
	@AfterEach
	public void teardown() {
		agent.stopServer();
	}

	/** Test overwriting the commit message */
	@Test
	public void testOverridingMessage() throws Exception {
		String newMessage = "New Message";

		putText("/message", newMessage);

		TeamscaleServer teamscaleServer = agent.options.teamscaleServer;
		assertThat(teamscaleServer.getMessage()).isEqualTo(newMessage);
	}

	/** Test reading the commit message */
	@Test
	public void testGettingMessage() throws Exception {
		String receivedMessage = getText("/message");

		assertThat(receivedMessage).isEqualTo(defaultCommitMessage);
	}

	/** Test overwriting the partition */
	@Test
	public void testOverridingPartition() throws Exception {
		String newPartition = "New Partition";

		putText("/partition", newPartition);

		TeamscaleServer teamscaleServer = agent.options.teamscaleServer;
		assertThat(teamscaleServer.partition).isEqualTo(newPartition);
	}

	/** Test reading the partition */
	@Test
	public void testGettingPartition() throws Exception {
		String receivedPartition = getText("/partition");

		assertThat(receivedPartition).isEqualTo(defaultPartition);
	}

	/** Test reading the commit to which the agent will upload. */
	@Test
	public void testGettingCommit() throws Exception {
		String receivedCommit = getText("/commit");
		assertThat(receivedCommit).isEqualTo("{\"type\":\"REVISION\",\"value\":null}");
		receivedCommit = getJson("/commit");
		assertThat(receivedCommit).isEqualTo("{\"type\":\"REVISION\",\"value\":null}");
	}

	/** Test reading the revision to which the agent will upload. */
	@Test
	public void testGettingRevision() throws Exception {
		String receivedRevision = getText("/revision");
		assertThat(receivedRevision).isEqualTo("{\"type\":\"REVISION\",\"value\":null}");
		receivedRevision = getJson("/revision");
		assertThat(receivedRevision).isEqualTo("{\"type\":\"REVISION\",\"value\":null}");
	}

	private void putText(String endpointPath, String newValue) throws Exception {
		OkHttpClient client = new OkHttpClient();
		MediaType textPlainMediaType = MediaType.parse("text/plain; charset=utf-8");
		HttpUrl endpointUrl = HttpUrl.get(baseUri.resolve(endpointPath));
		Request request = new Request.Builder()
				.url(endpointUrl)
				.method("PUT", RequestBody.create(textPlainMediaType, newValue.getBytes()))
				.build();
		client.newCall(request).execute();
	}

	private String getText(String endpointPath) throws Exception {
		OkHttpClient client = new OkHttpClient();
		HttpUrl endpointUrl = HttpUrl.get(baseUri.resolve(endpointPath));
		Request request = new Request.Builder()
				.url(endpointUrl)
				.build();
		Response response = client.newCall(request).execute();
		return response.body() != null ? response.body().string() : "";
	}

	private String getJson(String endpointPath) throws Exception {
		OkHttpClient client = new OkHttpClient();
		HttpUrl endpointUrl = HttpUrl.get(baseUri.resolve(endpointPath));
		Request request = new Request.Builder()
				.url(endpointUrl).header("Accept", javax.ws.rs.core.MediaType.APPLICATION_JSON)
				.build();
		Response response = client.newCall(request).execute();
		return response.body() != null ? response.body().string() : "";
	}
}
