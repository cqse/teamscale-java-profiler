package com.teamscale.jacoco.agent

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * Generates a [jakarta.ws.rs.core.Response] for an exception.
 */
@Provider
class GenericExceptionMapper : ExceptionMapper<Throwable?> {
	override fun toResponse(e: Throwable?): Response {
		val status = (e as? WebApplicationException)?.response?.statusInfo ?: Response.Status.INTERNAL_SERVER_ERROR
		return Response.status(status).apply {
			type(MediaType.TEXT_PLAIN_TYPE)
			entity("Message: ${e?.message}")
		}.build()
	}
}