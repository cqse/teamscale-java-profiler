package com.teamscale.test.commons

import com.fasterxml.jackson.core.JsonProcessingException
import com.teamscale.client.*
import com.teamscale.client.JsonUtils.deserializeList
import com.teamscale.client.JsonUtils.serializeToJson
import spark.Request
import spark.Response
import spark.Service
import spark.utils.IOUtils
import java.io.IOException
import java.nio.file.Files
import java.util.*
import javax.servlet.MultipartConfigElement
import javax.servlet.ServletException
import javax.servlet.http.HttpServletResponse

/**
 * Mocks a Teamscale server: returns predetermined impacted tests and stores all uploaded reports so tests can run
 * assertions on them.
 */
class TeamscaleMockServer(val port: Int) {
	/** All report upload sessions that were created.  */
	private val sessions = hashMapOf<String, Session>()

	/** All user agents that were present in the received requests.  */
	@JvmField
	val collectedUserAgents = mutableSetOf<String>()

	/** A list of all commits for which impacted tests were requested. Can either be branch:timestamp or revision  */
	@JvmField
	val impactedTestCommits = mutableListOf<String>()

	/** A list of all commits used as baseline for retrieving impacted tests  */
	@JvmField
	val baselines = mutableListOf<String>()

	/** All tests that the test engine has signaled to Teamscale as being available for execution.  */
	@JvmField
	val allAvailableTests = mutableSetOf<TestWithClusterId>()

	private val tempDir = Files.createTempDirectory("TeamscaleMockServer")
	private val service = Service.ignite()
	private var impactedTests = listOf<String>()
	val profilerEvents = mutableListOf<String>()

	/** All log entries that the profiler forwarded to this server via {@code POST /profilers/:profilerId/logs}. */
	val collectedLogMessages = mutableListOf<ProfilerLogEntry>()
	private var profilerConfiguration: ProfilerConfiguration? = null
	private var username: String? = null
	private var accessToken: String? = null

	init {
		service.port(port)
		service.exception<Exception>(Exception::class.java) { exception, _, response ->
			response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
			response.body("Exception: " + exception!!.message)
		}
		service.notFound { request, response ->
			response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
			"Unexpected request: ${request.requestMethod()} ${request.uri()}"
		}
		service.init()
		service.awaitInitialization()
	}

	/** Returns the URL of the mock server. */
	val url
		get() = "http://localhost:${port}"

	/** Resets the data collected by the mock server for the next test.  */
	fun reset() {
		sessions.clear()
		allAvailableTests.clear()
		profilerEvents.clear()
		collectedLogMessages.clear()
		impactedTestCommits.clear()
		baselines.clear()
	}

	/** Returns all committed sessions.  */
	fun getSessions() = sessions.values.filter { it.isCommitted }.sortedBy { it.partition }

	/** Returns the session with the given partition name  */
	fun getSession(partition: String) =
		getSessions().firstOrNull { it.partition == partition }
			?: throw AssertionError("No session found for partition: $partition")

	val onlySession: Session
		/** Asserts that there is only one session and returns it.  */
		get() {
			val sessions = getSessions()
			check(sessions.size == 1) {
				"Expected exactly one session, but got ${sessions.size}"
			}
			return sessions.first()
		}

	/** Asserts that there is only one session and that it has the expected partition name and returns it.  */
	fun getOnlySession(partition: String): Session {
		val session = onlySession
		check(session.partition == partition) {
			"Expected only session to have partition $partition but got ${session.partition}!"
		}
		return session
	}

	/**
	 * Asserts that there is exactly one session with the expected partition name, containing exactly one report in the
	 * given format and returns it.
	 */
	fun getOnlyReport(partition: String, format: EReportFormat): String {
		val session = getOnlySession(partition)
		return session.getOnlyReport(format)
	}

	/**
	 * Asserts that there is exactly one session with the expected partition name, containing exactly one Testwise
	 * Coverage report and returns a deserialized version of it.
	 */
	@Throws(JsonProcessingException::class)
	fun getOnlyTestwiseCoverageReport(partition: String) =
		getOnlySession(partition).onlyTestwiseCoverageReport

	/** Enables authentication for the mock server by setting the provided username and access token.  */
	fun withAuthentication(username: String, accessToken: String): TeamscaleMockServer {
		this.username = username
		this.accessToken = accessToken
		return this
	}

	/** Configures the server to accept report uploads and store them within the mock for later retrieval.  */
	fun acceptingReportUploads(): TeamscaleMockServer {
		service.post("api/v2024.7.0/projects/:projectId/external-analysis/session/:sessionId/report", ::handleReport)
		service.post("api/v2024.7.0/projects/:projectId/external-analysis/session", ::createSession)
		service.post("api/v2024.7.0/projects/:projectId/external-analysis/session/:sessionId", ::commitSession)
		return this
	}

	private fun commitSession(request: Request, response: Response): Any {
		requireAuthentication(request, response)
		sessions.get(request.params("sessionId"))?.markCommitted()
		return ""
	}

	@Throws(JsonProcessingException::class)
	private fun createSession(request: Request, response: Response): Any {
		requireAuthentication(request, response)

		val sessionId = UUID.randomUUID().toString()
		sessions[sessionId] = Session(request)
		return sessionId.serializeToJson()
	}

	/** Configures the server to answer all impacted test calls with the given tests.  */
	fun withImpactedTests(vararg impactedTests: String): TeamscaleMockServer {
		this.impactedTests = listOf(*impactedTests)
		service.put("api/v2024.7.0/projects/:projectName/impacted-tests", ::handleImpactedTests)
		return this
	}

	/** Configures the server to answer all impacted test calls with the given tests.  */
	fun withProfilerConfiguration(profilerConfiguration: ProfilerConfiguration?): TeamscaleMockServer {
		this.profilerConfiguration = profilerConfiguration
		service.post("api/v2024.7.0/profilers", ::handleProfilerRegistration)
		service.put("api/v2024.7.0/profilers/:profilerId", ::handleProfilerHeartbeat)
		service.delete("api/v2024.7.0/profilers/:profilerId", ::handleProfilerUnregister)
		service.post("api/v2024.7.0/profilers/:profilerId/logs", ::handleProfilerLogs)
		return this
	}

	/** Configures the server to answer all POST/PUT requests with an error.  */
	fun disallowingStateChanges(): TeamscaleMockServer {
		service.post("") { _, _ ->
			throw IllegalStateException("No POST to the mock server expected!")
		}
		service.put("") { _, _ ->
			throw IllegalStateException("No PUT to the mock server expected!")
		}
		return this
	}

	@Throws(IOException::class)
	private fun handleImpactedTests(request: Request, response: Response): String {
		requireAuthentication(request, response)

		request.collectUserAgent()
		impactedTestCommits.add(
			"${request.queryParams("end-revision")}:${request.queryParams("repository")}, ${
				request.queryParams(
					"end"
				)
			}"
		)
		baselines.add("${request.queryParams("baseline-revision")}, ${request.queryParams("baseline")}")
		val availableTests = deserializeList<TestWithClusterId>(request.body())
		allAvailableTests.addAll(availableTests)
		return availableTests
			.filter { availableTest -> impactedTests.contains(availableTest.testName) }
			.groupBy { testWithClusterId -> Optional.ofNullable(testWithClusterId.clusterId) }
			.map { (clusterId, impactedTests) ->
				PrioritizableTestCluster(
					clusterId.orElse(null),
					impactedTests.map { PrioritizableTest(it.testName) }
				)
			}.serializeToJson()
	}

	@Throws(JsonProcessingException::class)
	private fun handleProfilerRegistration(request: Request, response: Response): String {
		requireAuthentication(request, response)

		request.collectUserAgent()
		profilerEvents.add(
			"Profiler registered and requested configuration ${request.queryParams("configuration-id")}"
		)
		val registration = ProfilerRegistration()
		registration.profilerConfiguration = this.profilerConfiguration
		registration.profilerId = "123"
		return registration.serializeToJson()
	}

	private fun handleProfilerHeartbeat(request: Request, response: Response): String {
		requireAuthentication(request, response)

		request.collectUserAgent()
		profilerEvents.add("Profiler " + request.params(":profilerId") + " sent heartbeat")
		return ""
	}

	private fun handleProfilerLogs(request: Request, response: Response): String {
		requireAuthentication(request, response)

		request.collectUserAgent()
		profilerEvents.add("Profiler " + request.params(":profilerId") + " sent logs")
		try {
			collectedLogMessages.addAll(deserializeList<ProfilerLogEntry>(request.body()))
		} catch (e: JsonProcessingException) {
			// Body wasn't a list of ProfilerLogEntry — ignore so we don't break tests that don't care about log bodies.
		}
		return ""
	}

	private fun handleProfilerUnregister(request: Request, response: Response): String {
		requireAuthentication(request, response)

		request.collectUserAgent()
		profilerEvents.add("Profiler " + request.params(":profilerId") + " unregistered")
		return "foo"
	}

	@Throws(IOException::class, ServletException::class)
	private fun handleReport(request: Request, response: Response): String {
		requireAuthentication(request, response)

		val sessionId = request.params(":sessionId")
		val session: Session
		if (sessionId == "auto-create") {
			session = Session(request)
			session.markCommitted()
			sessions.put(UUID.randomUUID().toString(), session)
		} else {
			session = sessions.get(sessionId)!!
		}

		request.collectUserAgent()
		val multipartConfigElement = MultipartConfigElement(tempDir.toString())
		request.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement)

		val format = request.queryParams("format")
		request.raw().parts.forEach { part ->
			val reportString = IOUtils.toString(part.inputStream)
			session.addReport(format, reportString)
			part.delete()
		}

		return "success"
	}

	/**
	 * Shuts down the mock server and waits for it to be stopped.
	 */
	fun shutdown() {
		service.stop()
		service.awaitStop()
	}

	private fun requireAuthentication(request: Request, response: Response) {
		if (username != null && accessToken != null) {
			val authHeader = request.headers("Authorization")
			if (authHeader == null || authHeader != buildBasicAuthHeader(username, accessToken)) {
				response.status(401)
				throw IllegalArgumentException("Unauthorized")
			}
		}
	}

	private fun buildBasicAuthHeader(username: String?, accessToken: String?) =
		"Basic " + Base64.getEncoder().encodeToString("$username:$accessToken".toByteArray())

	private fun Request.collectUserAgent() {
		collectedUserAgents.add(headers("User-Agent"))
	}
}
