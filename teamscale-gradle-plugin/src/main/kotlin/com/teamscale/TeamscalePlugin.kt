package com.teamscale

import com.teamscale.aggregation.TestSuiteCompatibilityUtil
import com.teamscale.extension.TeamscalePluginExtension
import com.teamscale.extension.TeamscaleTaskExtension
import com.teamscale.utils.AgentPortGenerator
import com.teamscale.utils.BuildVersion
import com.teamscale.utils.jacoco
import com.teamscale.utils.testing
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.util.GradleVersion


/**
 * Root entry point for the Teamscale Gradle plugin.
 *
 * The plugin applies the Java plugin and a root extension named teamscale.
 * Each Test task configured in the project the plugin creates a new task suffixed with {@value #impactedTestsSuffix}
 * that executes the same set of tests, but additionally collects testwise coverage and executes only impacted tests.
 * Furthermore, all reports configured are uploaded to Teamscale after the tests have been executed.
 */
class TeamscalePlugin : Plugin<Project> {

	companion object {

		/** The name of the extension used to configure the plugin. */
		const val TEAMSCALE_EXTENSION_NAME = "teamscale"

		/** The name of the configuration that holds the impacted test executor and its dependencies. */
		const val IMPACTED_TEST_ENGINE_CONFIGURATION_NAME = "impactedTestsEngine"

		/** The name of the configuration that holds the teamscale jacoco agent and its dependencies. */
		const val TEAMSCALE_JACOCO_AGENT_CONFIGURATION_NAME = "teamscaleJaCoCoAgent"

		private val DEFAULT_EXCLUDES = listOf(
			"org.junit.*",
			"org.gradle.*",
			"com.esotericsoftware.*",
			"com.teamscale.jacoco.agent.*",
			"com.teamscale.test_impacted.*",
			"com.teamscale.report.*",
			"com.teamscale.client.*",
			"org.jacoco.core.*",
			"shadow.*",
			"okhttp3.*",
			"okio.*",
			"retrofit2.*",
			"*.MockitoMock.*",
			"*.FastClassByGuice.*",
			"*.ConstructorAccess"
		)

		/** The minimum supported Gradle version. */
		val MINIMUM_SUPPORTED_VERSION = GradleVersion.version("8.10")!!
	}

	/** The version of the teamscale gradle plugin and impacted-tests-executor.  */
	private val pluginVersion = BuildVersion.pluginVersion

	private lateinit var impactedTestEngineConfiguration: Configuration

	private lateinit var teamscaleJacocoAgentConfiguration: Configuration

	/** Applies the teamscale plugin against the given project.  */
	override fun apply(project: Project) {
		if (GradleVersion.current() < MINIMUM_SUPPORTED_VERSION) {
			throw GradleException("The teamscale plugin requires Gradle version ${MINIMUM_SUPPORTED_VERSION.version} or higher. Version detected: ${GradleVersion.current()}")
		}

		project.logger.info("Applying teamscale plugin $pluginVersion to ${project.name}")
		project.plugins.apply {
			apply(JavaPlugin::class.java)
			apply(JacocoPlugin::class.java)
			apply(ReportingBasePlugin::class.java)
			apply(JvmTestSuitePlugin::class.java)
		}

		val pluginExtension =
			project.extensions.create(TEAMSCALE_EXTENSION_NAME, TeamscalePluginExtension::class.java, project.layout)

		// Add impacted tests executor to a custom configuration that will later be appended to the test classpath
		// if testwise coverage collection is enabled
		impactedTestEngineConfiguration = project.configurations.create(IMPACTED_TEST_ENGINE_CONFIGURATION_NAME)

		// Add teamscale jacoco agent to a custom configuration that will later be used
		// to generate testwise coverage if enabled.
		teamscaleJacocoAgentConfiguration = project.configurations.create(TEAMSCALE_JACOCO_AGENT_CONFIGURATION_NAME)

		project.dependencies {
			impactedTestEngineConfiguration("com.teamscale:impacted-test-engine:$pluginVersion")
			teamscaleJacocoAgentConfiguration("com.teamscale:teamscale-jacoco-agent:$pluginVersion")
		}

		configureTestTasks(project, pluginExtension)
		configureTeamscaleUploadTasks(project, pluginExtension)

		// Auto-expose JUnit and Testwise coverage reports for test tasks bound to JVM test suites
		project.testing.suites.withType<JvmTestSuite> {
			val suite = this
			suite.targets.configureEach {
				TestSuiteCompatibilityUtil.exposeTestReportArtifactsForAggregation(project, testTask, suite.name)
			}
			project.configurations.named(suite.sources.runtimeOnlyConfigurationName) {
				extendsFrom(impactedTestEngineConfiguration)
			}
		}
	}

	private fun configureTeamscaleUploadTasks(
		project: Project,
		pluginExtension: TeamscalePluginExtension
	) {
		project.tasks.withType<TeamscaleUpload> {
			serverConfiguration.convention(pluginExtension.server)
			repository.convention(pluginExtension.repository)
			commitDescriptorOrRevision.convention(pluginExtension.commit.combined)
			ignoreFailures.convention(false)
			message.convention(partition.map { "$it Gradle upload" })
		}
	}

	private fun configureTestTasks(
		project: Project,
		teamscalePluginExtension: TeamscalePluginExtension
	) {
		val agentPortGenerator: Provider<AgentPortGenerator> = project.gradle.sharedServices.registerIfAbsent(
			"agent-port-generator",
			AgentPortGenerator::class.java
		) {}
		project.tasks.withType<Test> {
			jacoco.excludes?.let { buildExcludes -> jacoco.excludes = buildExcludes + DEFAULT_EXCLUDES }

			val extension = this.extensions.create<TeamscaleTaskExtension>(
				TEAMSCALE_EXTENSION_NAME, teamscaleJacocoAgentConfiguration, jacoco
			).apply {
				collectTestwiseCoverage.convention(false)
				runImpacted.convention(false)
				runAllTests.convention(false)
				includeAddedTests.convention(true)
				includeFailedAndSkipped.convention(true)
				val port = agentPortGenerator.get().getNextPort()
				agent.useLocalAgent("http://127.0.0.1:${port}/")
				agent.destination.set(project.layout.buildDirectory.dir("jacoco/${this@withType.name}"))
			}

			doFirst("testImpactConfiguration", TestImpactConfigurationAction(teamscalePluginExtension, extension))

			outputs.doNotCacheIf("When using Test Impact Analysis ") { extension.runImpacted.get() }
			outputs.dir(extension.agent.destination)
		}
	}
}

