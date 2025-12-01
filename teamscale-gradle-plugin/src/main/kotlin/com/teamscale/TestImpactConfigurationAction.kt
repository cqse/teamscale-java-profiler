package com.teamscale

import com.teamscale.extension.TeamscalePluginExtension
import com.teamscale.extension.TeamscaleTaskExtension
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

/**
 * Task action which is attached to all tasks of type [Test] that configures the task
 * to run the tests through the impacted-test engine if [TeamscaleTaskExtension.collectTestwiseCoverage] or
 * [TeamscaleTaskExtension.runImpacted] is enabled. */
class TestImpactConfigurationAction(
	private val pluginExtension: TeamscalePluginExtension,
	private val extension: TeamscaleTaskExtension
) : Action<Task> {

	companion object {
		private const val IMPACTED_TEST_ENGINE_ID = "teamscale-test-impacted"
	}

	override fun execute(test: Task) {
		(test as Test).configureTestTask()
	}

	private fun Test.configureTestTask() {
		val testFrameworkOptions = options
		if (testFrameworkOptions !is JUnitPlatformOptions) {
			check(!extension.collectTestwiseCoverage.get() && !extension.runImpacted.get()) { "Only JUnit Platform is supported as test framework for collecting testwise coverage!" }
			return
		}
		if (extension.collectTestwiseCoverage.get() || extension.runImpacted.get()) {
			check(maxParallelForks == 1) { "maxParallelForks is ${maxParallelForks}. Testwise coverage collection is only supported for maxParallelForks=1!" }

			if (extension.runImpacted.get()) {
				// Workaround to not cause the task to fail when no tests are executed, which might happen when no tests are impacted
				// We do so by adding a useless filter, because the "no tests executed" can only be disabled for the case where filters are applied
				// https://docs.gradle.org/8.8/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed
				// https://github.com/gradle/gradle/blob/57cc16c8fbf4116a0ef0ad7b742c1a4a4e11a474/platforms/software/testing-base/src/main/java/org/gradle/api/tasks/testing/AbstractTestTask.java#L542
				filter.excludeTestsMatching("dummy-test-name")
				filter.isFailOnNoMatchingTests = false
			}

			jvmArgumentProviders.removeIf { it.javaClass.name.contains("JacocoPluginExtension") }

			extension.agent.localAgent?.let {
				jvmArgs(it.getJvmArgs())
			}

			setImpactedTestEngineOptions()
			writeProperty("includedEngines", testFrameworkOptions.includeEngines.joinToString(","))
			writeProperty("excludedEngines", testFrameworkOptions.excludeEngines.joinToString(","))
			testFrameworkOptions.includeEngines = setOf(IMPACTED_TEST_ENGINE_ID)
		} else {
			testFrameworkOptions.excludeEngines.add(IMPACTED_TEST_ENGINE_ID)
		}
	}

	private fun Test.writeProperty(key: String, value: Any?) {
		value?.let { systemProperties["teamscale.test.impacted.${key}"] = it.toString() }
	}

	/**
	 * Sets the values configured in the task and plugin extension as system properties
	 * that will be passed to the impacted test engine.
	 */
	private fun Test.setImpactedTestEngineOptions() {
		if (extension.runImpacted.get()) {
			val combinedCommit = pluginExtension.commit.combined
			val combinedBaseline = pluginExtension.baseline.combined
			check(combinedCommit.isPresent) { "When executing only impacted tests a reference commit must be specified in the form of endRevision or endCommit!" }
			check(
				extension.partition.isPresent && extension.partition.get().isNotBlank()
			) { "Partition is required for retrieving Test Impact Analysis results" }
			pluginExtension.server.validate()
			writeProperty("server.url", pluginExtension.server.url.get())
			writeProperty("server.project", pluginExtension.server.project.get())
			writeProperty("server.userName", pluginExtension.server.userName.get())
			writeProperty("server.userAccessToken", pluginExtension.server.userAccessToken.get())
			writeProperty("baseline", combinedBaseline.orNull?.timestamp)
			writeProperty("baselineRevision", combinedBaseline.orNull?.revision)
			writeProperty("partition", extension.partition.get())
			writeProperty("endCommit", combinedCommit.get().commit)
			writeProperty("endRevision", combinedCommit.get().revision)
			writeProperty("repository", pluginExtension.repository.orNull)
		}
		writeProperty("enabled", true)
		writeProperty("reportDirectory", extension.agent.destination.asFile.get().absolutePath)
		writeProperty("agentsUrls", extension.agent.allAgentUrls.joinToString(","))
		writeProperty("runImpacted", extension.runImpacted.get())
		writeProperty("runAllTests", extension.runAllTests.get())
		writeProperty("includeAddedTests", extension.includeAddedTests.get())
		writeProperty("includeFailedAndSkipped", extension.includeFailedAndSkipped.get())

		if (extension.debugLogging.getOrElse(false)) {
			writeProperty("logFilePath", extension.agent.destination.asFile.get().resolve("engine.log").absolutePath)
			writeProperty("logLevel", "DEBUG")
		}
	}
}

