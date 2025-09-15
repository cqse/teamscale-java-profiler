package com.teamscale.test.commons

import com.teamscale.client.SystemUtils
import com.teamscale.report.testwise.model.TestInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * Utilities for running system tests.
 */
object SystemTestUtils {
	/**
	 * The port for the mock Teamscale server that was picked by the Gradle build script and is guaranteed to not
	 * conflict with other system tests.
	 */
	@JvmField
	val TEAMSCALE_PORT: Int = Integer.getInteger("teamscalePort")

	/**
	 * The port for the agent that was picked by the Gradle build script and is guaranteed to not conflict with other
	 * system tests.
	 */
	@JvmField
	val AGENT_PORT: Int = Integer.getInteger("agentPort")

	/**
	 * Turns the coverage of the given [TestInfo] into a string for simple assertions.
	 *
	 *
	 * Example: `file1.java:1,7-12;file2.java:9-22,33`
	 */
	@JvmStatic
	val TestInfo.coverage
		get() = paths.flatMap { it.files }.joinToString(";") { "${it.fileName}:${it.coveredLines}" }

	/**
	 * Runs the clean and verify goal of the Maven project at the given path with the provided arguments
	 *
	 * @throws IOException if running Maven fails.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun runMavenTests(mavenProjectPath: String, vararg args: String) {
		val allArguments = mutableListOf("clean", "verify").apply {
			addAll(args)
		}
		runMaven(mavenProjectPath, *allArguments.toTypedArray())
	}

	/**
	 * Runs Maven in the given Maven project path with the given arguments.
	 *
	 * @throws IOException if running Maven fails.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun runMaven(mavenProjectPath: String, vararg mavenArguments: String) {
		val result = buildMavenProcess(mavenProjectPath, *mavenArguments).execute()

		// in case the process succeeded, we still log stdout and stderr in case later assertions fail. This helps
		// debug test failures
		println("Maven stdout: ${result.stdout}")
		println("Maven stderr: ${result.stderr}")

		if (result.wasTerminatedByTimeoutOrInterruption) {
			throw IOException("Running Maven failed: ${result.stdout}\n${result.stderr}")
		}
	}

	/**
	 * Runs Gradle in the given Gradle project path with the given arguments.
	 *
	 * @throws IOException if running Gradle fails.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun runGradle(gradleProjectPath: String, vararg gradleArguments: String): ProcessUtils.ProcessResult {
		val result = buildGradleProcess(gradleProjectPath, *gradleArguments).execute()

		// in case the process succeeded, we still log stdout and stderr in case later assertions fail. This helps
		// debug test failures
		println("Gradle stdout: ${result.stdout}")
		println("Gradle stderr: ${result.stderr}")

		if (result.wasTerminatedByTimeoutOrInterruption) {
			throw IOException("Running Gradle failed: ${result.stdout}\n${result.stderr}")
		}
		return result
	}

	/**
	 * Creates the command-line arguments that can be passed to [ProcessBuilder] to invoke Maven with the given
	 * arguments.
	 */
	@JvmStatic
	fun buildMavenProcess(mavenProjectDirectory: String, vararg mavenArguments: String): ProcessUtils.ProcessExecutor {
		val arguments = mutableListOf<String>()
		if (SystemUtils.IS_OS_WINDOWS) {
			arguments.addAll(listOf("cmd", "/c", "mvnw.cmd"))
		} else {
			arguments.add("./mvnw")
		}

		arguments.addAll(listOf(*mavenArguments))

		return ProcessUtils.processBuilder(arguments).directory(File(mavenProjectDirectory))
	}

	/**
	 * Creates the command-line arguments that can be passed to [ProcessBuilder] to invoke Gradle with the given
	 * arguments.
	 */
	private fun buildGradleProcess(
		gradleProjectDirectory: String,
		vararg gradleArguments: String
	): ProcessUtils.ProcessExecutor {
		val arguments = mutableListOf<String>()
		if (SystemUtils.IS_OS_WINDOWS) {
			arguments.addAll(listOf("cmd", "/c", "gradlew.bat"))
		} else {
			arguments.add("./gradlew")
		}

		arguments.addAll(listOf(*gradleArguments))

		return ProcessUtils.processBuilder(arguments).directory(File(gradleProjectDirectory))
	}

	/** Retrieve all files in the `tia/reports` folder sorted by name.  */
	@JvmStatic
	@Throws(IOException::class)
	fun getReportFileNames(mavenProjectPath: String, folderName: String): List<Path> {
		Files.walk(Paths.get(mavenProjectPath, "target", folderName, "reports")).use { stream ->
			return stream.asSequence().filter { Files.isRegularFile(it) }.sorted().toList()
		}
	}

	/** Instructs the agent via HTTP to dump the currently collected coverage.  */
	@JvmStatic
	@Throws(IOException::class)
	fun dumpCoverage(agentPort: Int) {
		Retrofit.Builder().baseUrl("http://localhost:$agentPort").build()
			.create<AgentService>().dump().execute()
	}

	/** Instructs the agent via HTTP to change the partition.  */
	@JvmStatic
	@Throws(IOException::class)
	fun changePartition(agentPort: Int, newPartition: String) {
		val requestBody = newPartition.toRequestBody("text/plain".toMediaTypeOrNull())
		Retrofit.Builder().baseUrl("http://localhost:$agentPort").build()
			.create<AgentService>()
			.changePartition(requestBody)
			.execute()
	}

	/**
	 * Interface for interacting with an agent service that enables coverage dumping and partition management.
	 */
	interface AgentService {
		/** Dumps coverage  */
		@POST("/dump")
		fun dump(): Call<Void>

		/** Changes the current partition.  */
		@PUT("/partition")
		fun changePartition(@Body newPartition: RequestBody): Call<Void>
	}
}
