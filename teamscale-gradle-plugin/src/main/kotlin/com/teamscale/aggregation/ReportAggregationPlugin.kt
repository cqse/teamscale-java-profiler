package com.teamscale.aggregation

import com.teamscale.aggregation.ReportAggregationPlugin.Companion.AGGREGATE_REPORT_RESULTS_CONFIGURATION_NAME
import com.teamscale.aggregation.ReportAggregationPlugin.Companion.REPORT_AGGREGATION_CONFIGURATION_NAME
import com.teamscale.utils.classDirectories
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject


/**
 * Plugin that supports aggregating Testwise Coverage binary-data, JaCoCo binary data and JUnit reports across projects.
 *
 * It defines a consumable configuration [REPORT_AGGREGATION_CONFIGURATION_NAME],
 * that is used to collect all projects to aggregate reports from and a resolvable configuration
 * [AGGREGATE_REPORT_RESULTS_CONFIGURATION_NAME] that can be used by the upload task to resolve the reports.
 *
 * If the aggregating project is a Java project itself, we add the project as a dependency
 * to inherit all projects that are on the projects runtime classpath by default.
 *
 * If the aggregation project is standalone, it needs
 * to manually add a dependency on all projects that should be aggregated
 * to the [REPORT_AGGREGATION_CONFIGURATION_NAME] configuration.
 */
abstract class ReportAggregationPlugin : Plugin<Project> {

	companion object {

		private const val REPORT_AGGREGATION_CONFIGURATION_NAME = "reportAggregation"

		/**
		 * The resolvable configuration extending from [REPORT_AGGREGATION_CONFIGURATION_NAME]
		 * based on which the aggregation artifacts are collected.
		 */
		const val AGGREGATE_REPORT_RESULTS_CONFIGURATION_NAME = "aggregateReportResults"

	}

	/** The JVM plugin services. */
	@get:Inject
	protected abstract val jvmPluginServices: JvmPluginServices

	@Suppress("DEPRECATION")
	override fun apply(project: Project) {
		val configurations = project.configurations
		val reportAggregation =
			configurations.dependencyScope(REPORT_AGGREGATION_CONFIGURATION_NAME) {
				description = "A configuration to collect reports across projects."
				isVisible = false
			}.get()

		val aggregateReportResults = configurations.resolvable(AGGREGATE_REPORT_RESULTS_CONFIGURATION_NAME) {
			extendsFrom(reportAggregation)
			description =
				"Resolvable configuration used to gather files for report aggregation via ArtifactViews, not intended to be used directly"
			isVisible = false
		}.get()

		project.plugins.withType<JavaBasePlugin> {
			reportAggregation.dependencies.add(project.dependencyFactory.create(project))
			// If the current project is jvm-based, aggregate dependent projects as jvm-based as well.
			jvmPluginServices.configureAsRuntimeElements(aggregateReportResults)
		}
	}
}

/**
 * The resolvable configuration extending from [REPORT_AGGREGATION_CONFIGURATION_NAME]
 * based on which the aggregation artifacts are collected.
 */
val ConfigurationContainer.aggregateReportResults
	get() = getByName(AGGREGATE_REPORT_RESULTS_CONFIGURATION_NAME)

/** Returns all class directories from dependant projects. */
fun Configuration.classDirectories(objectFactory: ObjectFactory) = incoming.artifactView {
	filterProject()
	attributes.classDirectories(objectFactory)
}.files

/** Filters the artifact view to project dependencies only (i.e., excluding third-party dependencies). */
fun ArtifactView.ViewConfiguration.filterProject() {
	componentFilter { it is ProjectComponentIdentifier }
}
