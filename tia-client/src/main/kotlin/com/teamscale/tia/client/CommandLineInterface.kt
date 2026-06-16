package com.teamscale.tia.client

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.JsonUtils.deserializeList
import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.tia.client.AgentCommunicationUtils.handleRequestError
import com.teamscale.tia.client.UrlUtils.encodeUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors

/**
 * Simple command-line interface to expose the [TiaAgent] to non-Java test runners.
 */
class CommandLineInterface(arguments: Array<String>) {
	private class InvalidCommandLineException(message: String?) : RuntimeException(message)

	private val arguments = listOf(*arguments).toMutableList()
	private val command: String
	private val api: ITestwiseCoverageAgentApi

	init {
		if (arguments.size < 2) {
			throw InvalidCommandLineException(
				"You must provide at least two arguments: the agent's URL and a command." +
						" Usage: <agent-url> <startTestRun|startTest|endTest|endTestRun> [options]."
			)
		}

		val url = this.arguments.removeAt(0).toHttpUrl()
		api = ITestwiseCoverageAgentApi.createService(url)

		command = this.arguments.removeAt(0)
	}

	@Throws(Exception::class)
	private fun runCommand() {
		when (command) {
			"startTestRun" -> startTestRun()
			"startTest" -> startTest()
			"endTest" -> endTest()
			"endTestRun" -> endTestRun()
			else -> throw InvalidCommandLineException(
				"Unknown command '$command'. Should be one of startTestRun, startTest, endTest, endTestRun"
			)
		}
	}

	@Throws(Exception::class)
	private fun endTestRun() {
		val partial = if (arguments.size == 1) {
			arguments.removeAt(0).toBoolean()
		} else {
			false
		}
		handleRequestError(
			"Failed to create a coverage report and upload it to Teamscale. The coverage is most likely lost"
		) { api.testRunFinished(partial) }
	}

	@Throws(Exception::class)
	private fun endTest() {
		if (arguments.size < 2) {
			throw InvalidCommandLineException(
				"You must provide the uniform path of the test that is about to be started as the first argument of the endTest command and the test result as the second."
			)
		}
		val uniformPath = arguments.removeAt(0)
		val result = ETestExecutionResult.valueOf(arguments.removeAt(0).uppercase(Locale.getDefault()))
		val message = readStdin()

		// the agent already records test duration, so we can simply provide a dummy value here
		val execution = TestExecution(uniformPath, 0L, result, message)
		handleRequestError(
			"Failed to end coverage recording for test case $uniformPath. Coverage for that test case is most likely lost."
		) { api.testFinished(uniformPath.encodeUrl(), execution) }
	}

	@Throws(Exception::class)
	private fun startTest() {
		if (arguments.size < 1) {
			throw InvalidCommandLineException(
				"You must provide the uniform path of the test that is about to be started" +
						" as the first argument of the startTest command"
			)
		}
		val uniformPath = arguments.removeAt(0)
		handleRequestError(
			"Failed to start coverage recording for test case $uniformPath. Coverage for that test case is lost."
		) { api.testStarted(uniformPath.encodeUrl()) }
	}

	@Throws(Exception::class)
	private fun startTestRun() {
		val includeNonImpacted = parseAndRemoveBooleanSwitch("include-non-impacted")
		val baseline = parseAndRemoveLongParameter("baseline")
		val baselineRevision = parseAndRemoveStringParameter("baseline-revision")
		val availableTests = parseAvailableTestsFromStdin()

		handleRequestError(
			"Failed to start the test run"
		) {
			api.testRunStarted(includeNonImpacted, baseline, baselineRevision, availableTests)
		}?.let {
			println(it.serializeToJson())
		}
	}

	@Throws(IOException::class)
	private fun parseAvailableTestsFromStdin(): List<ClusteredTestDetails> {
		val json = readStdin()
		var availableTests = emptyList<ClusteredTestDetails>()
		if (!isEmpty(json)) {
			availableTests = deserializeList(json)
		}
		return availableTests
	}

	private fun readStdin(): String {
		return BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8)).lines()
			.collect(Collectors.joining("\n"))
	}

	private fun parseAndRemoveLongParameter(name: String): Long? {
		for (i in arguments.indices) {
			if (arguments[i].startsWith("--$name=")) {
				val argument = arguments.removeAt(i)
				return argument.substring(name.length + 3).toLong()
			}
		}
		return null
	}

	private fun parseAndRemoveBooleanSwitch(name: String): Boolean {
		for (i in arguments.indices) {
			if (arguments[i] == "--$name") {
				arguments.removeAt(i)
				return true
			}
		}
		return false
	}

	private fun parseAndRemoveStringParameter(name: String): String? {
		for (i in arguments.indices) {
			if (arguments[i].startsWith("--$name=")) {
				val argument = arguments.removeAt(i)
				return argument.substring(name.length + 3)
			}
		}
		return null
	}

	companion object {
		/** Entry point.  */
		@Throws(Exception::class)
		@JvmStatic
		fun main(arguments: Array<String>) {
			CommandLineInterface(arguments).runCommand()
		}
	}
}
