package com.teamscale

import com.teamscale.plugin.fixtures.TeamscaleConstants
import com.teamscale.plugin.fixtures.TestRootProject
import com.teamscale.test.commons.TeamscaleMockServer
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.management.ManagementFactory


/**
 * Integration tests for the Teamscale Gradle plugin.
 */
abstract class TeamscalePluginTestBase {

	companion object {
		/**
		 * The `-javaagent` option that records the coverage of this plugin, supplied by the
		 * com.teamscale.spawned-jvm-coverage convention plugin. Absent unless that plugin is applied.
		 *
		 * TestKit runs every build below in a daemon of its own, so without this the plugin would be entirely
		 * uncovered even though these tests exercise all of it. `GRADLE_OPTS` would only reach the launcher,
		 * which is why the daemon is asked for the agent through `org.gradle.jvmargs`. The option is dropped
		 * when it contains whitespace, which would break the command line, cf. ProcessUtils.
		 */
		private val coverageAgent: String? =
			System.getProperty("systemTestCoverageAgent")?.takeIf { it.none(Char::isWhitespace) }
	}

	/** Teamscale mock server to be used during the tests. */
	protected lateinit var teamscaleMockServer: TeamscaleMockServer

	@BeforeEach
	fun startFakeTeamscaleServer() {
		teamscaleMockServer = TeamscaleMockServer(TeamscaleConstants.PORT)
			.withAuthentication(TeamscaleConstants.USER, TeamscaleConstants.ACCESS_TOKEN)
			.acceptingReportUploads()
			.withImpactedTests("com/example/project/JUnit4Test/systemTest")
	}

	@AfterEach
	fun serverShutdown() {
		teamscaleMockServer.shutdown()
	}

	/** The Gradle project in which the simulated checkout and test execution will happen. */
	lateinit var rootProject: TestRootProject

	@BeforeEach
	fun setup(@TempDir tempDir: File) {
		rootProject = TestRootProject(tempDir)
	}

	/** Runs Gradle with the given arguments and fails if the execution was not successful. */
	protected fun run(vararg arguments: String): BuildResult {
		return buildRunner(*arguments).build()
	}

	/** Runs Gradle with the given arguments and assumes that the build will fail with an error. */
	protected fun runExpectingError(vararg arguments: String): BuildResult {
		return buildRunner(*arguments).buildAndFail()
	}

	private fun buildRunner(vararg arguments: String): GradleRunner {
		val runnerArgs = arguments.toMutableList()
		val runner = GradleRunner.create()
		runner.forwardOutput()
		runnerArgs.add("--stacktrace")
		coverageAgent?.let { runnerArgs.add("-Dorg.gradle.jvmargs=$it") }

		if (ManagementFactory.getRuntimeMXBean().inputArguments.toString()
				.contains("-agentlib:jdwp")
		) {
			runner.withDebug(true)
			runnerArgs.add("--refresh-dependencies")
			runnerArgs.add("--info")
			if (arguments.contains("unitTest")) {
				runnerArgs.add(arguments.indexOf("unitTest") + 1, "--debug-jvm")
			}
		}

		runner
			.withProjectDir(rootProject.projectDir)
			.withPluginClasspath()
			.withArguments(runnerArgs)
			.withGradleVersion(TeamscalePlugin.MINIMUM_SUPPORTED_VERSION.version)

		return runner
	}
}
