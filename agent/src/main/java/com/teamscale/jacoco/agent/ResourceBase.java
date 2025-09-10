package com.teamscale.jacoco.agent;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.StringUtils;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.testimpact.TestwiseCoverageAgent;
import com.teamscale.report.testwise.model.RevisionInfo;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;


/**
 * The resource of the Jersey + Jetty http server holding all the endpoints specific for the {@link AgentBase}.
 */
public abstract class ResourceBase {

	/** The logger. */
	protected final Logger logger = LoggingUtils.getLogger(this);

	/**
	 * The agentBase inject via {@link AgentResource#setAgent(Agent)} or
	 * {@link com.teamscale.jacoco.agent.testimpact.TestwiseCoverageResource#setAgent(TestwiseCoverageAgent)}.
	 */
	protected static AgentBase agentBase;

	/** Returns the partition for the Teamscale upload. */
	@GET
	@Path("/partition")
	public String getPartition() {
		return Optional.ofNullable(agentBase.options.getTeamscaleServerOptions().partition).orElse("");
	}

	/** Returns the upload message for the Teamscale upload. */
	@GET
	@Path("/message")
	public String getMessage() {
		return Optional.ofNullable(agentBase.options.getTeamscaleServerOptions().getMessage())
				.orElse("");
	}

	/** Returns revision information for the Teamscale upload. */
	@GET
	@Path("/revision")
	@Produces(MediaType.APPLICATION_JSON)
	public RevisionInfo getRevision() {
		return this.getRevisionInfo();
	}

	/** Returns revision information for the Teamscale upload. */
	@GET
	@Path("/commit")
	@Produces(MediaType.APPLICATION_JSON)
	public RevisionInfo getCommit() {
		return this.getRevisionInfo();
	}

	/** Handles setting the partition name. */
	@PUT
	@Path("/partition")
	public Response setPartition(String partitionString) {
		String partition = StringUtils.removeDoubleQuotes(partitionString);
		if (partition == null || partition.isEmpty()) {
			handleBadRequest("The new partition name is missing in the request body! Please add it as plain text.");
		}

		logger.debug("Changing partition name to " + partition);
		agentBase.dumpReport();
		agentBase.controller.setSessionId(partition);
		agentBase.options.getTeamscaleServerOptions().partition = partition;
		return Response.noContent().build();
	}

	/** Handles setting the upload message. */
	@PUT
	@Path("/message")
	public Response setMessage(String messageString) {
		String message = StringUtils.removeDoubleQuotes(messageString);
		if (message == null || message.isEmpty()) {
			handleBadRequest("The new message is missing in the request body! Please add it as plain text.");
		}

		agentBase.dumpReport();
		logger.debug("Changing message to " + message);
		agentBase.options.getTeamscaleServerOptions().setMessage(message);

		return Response.noContent().build();
	}

	/** Handles setting the revision. */
	@PUT
	@Path("/revision")
	public Response setRevision(String revisionString) {
		String revision = StringUtils.removeDoubleQuotes(revisionString);
		if (revision == null || revision.isEmpty()) {
			handleBadRequest("The new revision name is missing in the request body! Please add it as plain text.");
		}

		agentBase.dumpReport();
		logger.debug("Changing revision name to " + revision);
		agentBase.options.getTeamscaleServerOptions().revision = revision;

		return Response.noContent().build();
	}

	/** Handles setting the upload commit. */
	@PUT
	@Path("/commit")
	public Response setCommit(String commitString) {
		String commit = StringUtils.removeDoubleQuotes(commitString);
		if (commit == null || commit.isEmpty()) {
			handleBadRequest("The new upload commit is missing in the request body! Please add it as plain text.");
		}

		agentBase.dumpReport();
		agentBase.options.getTeamscaleServerOptions().commit = CommitDescriptor.parse(commit);

		return Response.noContent().build();
	}

	/** Returns revision information for the Teamscale upload. */
	private RevisionInfo getRevisionInfo() {
		TeamscaleServer server = agentBase.options.getTeamscaleServerOptions();
		return new RevisionInfo(server.commit, server.revision);
	}

	/**
	 * Handles bad requests to the endpoints.
	 */
	@Contract(value = "_ -> fail")
	protected void handleBadRequest(String message) throws BadRequestException {
		logger.error(message);
		throw new BadRequestException(message);
	}

}
