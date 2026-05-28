package com.teamscale.jacoco.agent

import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

/**
 * The resource of the Jersey + Jetty http server holding all the endpoints specific for the [Agent].
 */
@Path("/")
class AgentResource(private val agent: Agent) : ResourceBase(agent) {
	/** Handles dumping a XML coverage report for coverage collected until now.  */
	@POST
	@Path("/dump")
	fun handleDump(): Response? {
		logger.debug("Dumping report triggered via HTTP request")
		agent.dumpReport()
		return Response.noContent().build()
	}

	/** Handles resetting of coverage.  */
	@POST
	@Path("/reset")
	fun handleReset(): Response? {
		logger.debug("Resetting coverage triggered via HTTP request")
		agent.controller.reset()
		return Response.noContent().build()
	}
}
