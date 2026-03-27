package com.teamscale.jacoco.agent

import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

/**
 * The resource of the Jersey + Jetty http server holding all the endpoints specific for the [Agent].
 */
@Path("/")
class AgentResource : ResourceBase() {
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

	companion object {
		private lateinit var agent: Agent

		/**
		 * Static setter to inject the [Agent] to the resource.
		 */
		fun setAgent(agent: Agent) {
			Companion.agent = agent
			agentBase = agent
		}
	}
}