package com.teamscale.extension

import com.teamscale.config.AgentConfiguration
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.newInstance
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import java.io.Serializable
import javax.inject.Inject

/**
 * Holds all user configuration regarding testwise coverage report uploads.
 */
@Suppress("unused")
abstract class TeamscaleTaskExtension @Inject constructor(
	objectFactory: ObjectFactory,
	private val providerFactory: ProviderFactory,
	teamscaleJaCoCoAgentConfiguration: FileCollection,
	jacocoExtension: JacocoTaskExtension
) : Serializable {

	/** Settings regarding the teamscale jacoco agent. */
	val agent =
		objectFactory.newInstance<AgentConfiguration>(teamscaleJaCoCoAgentConfiguration, jacocoExtension, debugLogging)

	/** Configures the jacoco agent options. */
	fun agent(action: Action<in AgentConfiguration>) {
		action.execute(agent)
	}

	/** Command line switch to enable/disable testwise coverage collection. */
	abstract val collectTestwiseCoverage: Property<Boolean>

	/**
	 * If set to true, the plugin connects to Teamscale
	 * to retrieve which tests are impacted by a change and an optimized order in which they should be executed.
	 * The relevant changeset is defined by the commit and baseline option in the plugin extension.
	 */
	abstract val runImpacted: Property<Boolean>

	/**
	 * When set to true runs all tests even those that are not impacted.
	 * Teamscale still tries to optimize the execution order to cause failures early.
	 */
	abstract val runAllTests: Property<Boolean>

	/** When set to true, includes added tests in test selection. */
	abstract val includeAddedTests: Property<Boolean>

	/** Whether to include or exclude failed and skipped tests. */
	abstract val includeFailedAndSkipped: Property<Boolean>

	/** The partition in Teamscale that will be used to look up impacted tests. */
	abstract val partition: Property<String>

	/** Whether to enable debug logging for the impacted test engine. */
	abstract val debugLogging: Property<Boolean>

	/**
	 * Provider lazily determining whether the test will only run the tests partially
	 * according to the #runImpacted and #runAllTests options.
	 */
	internal val partial: Provider<Boolean>
		get() = providerFactory.zip(runImpacted, runAllTests)
		{ runImpacted, runAllTests -> runImpacted && !runAllTests }
}



