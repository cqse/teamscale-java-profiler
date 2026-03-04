package com.teamscale.reporting.testwise

import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.reporting.JaCoCoBasedReportTaskBase
import com.teamscale.reporting.testwise.internal.DefaultTestwiseCoverageTaskReportContainer
import com.teamscale.reporting.testwise.internal.TestwiseCoverageReporting
import com.teamscale.utils.reporting
import com.teamscale.utils.teamscale
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.work.DisableCachingByDefault

/** Task which runs the impacted tests. */
@DisableCachingByDefault(because = "Coverage reports depend on runtime execution data")
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class TestwiseCoverageReport : JaCoCoBasedReportTaskBase<TestwiseCoverageTaskReportContainer>() {

	/** Whether the report contains only partial data (i.e., not all tests have been executed). */
	@get:Input
	abstract val partial: Property<Boolean>

	override val reportContainer: TestwiseCoverageTaskReportContainer =
		objectFactory.newInstance(DefaultTestwiseCoverageTaskReportContainer::class.java)

	init {
		description = "Generates a testwise coverage report"

		reportContainer.testwiseCoverage.required.convention(true)
		reportContainer.testwiseCoverage.outputLocation.convention(project.reporting.baseDirectory.file("testwise-coverage/${name}.json"))
	}

	/**
	 * Generates a testwise coverage from the execution data and merges it with eventually existing closure coverage.
	 */
	@TaskAction
	override fun generateReport() {
		check(classDirectories.files.isNotEmpty()) { "The classDirectories does not contain any input! Did you forget to configure sourceSets on the task?" }
		logger.info("Generating coverage reports...")
		TestwiseCoverageReporting(
			logger,
			partial.get(),
			classDirectories.files.filter { it.exists() },
			ClasspathWildcardIncludeFilter(null, null),
			executionData.files.filter { it.exists() },
			reportContainer.testwiseCoverage.outputLocation.asFile.get()
		).generateTestwiseCoverageReports()
	}

	/**
	 * Sets the execution data, classDirectories and partial flag based on the given test task.
	 * Only tasks of type [Test] will be accepted.
	 */
	fun executionData(vararg tasks: Task) {
		for (task in tasks) {
			executionData(task)
		}
	}

	/**
	 * Sets the execution data, classDirectories and partial flag based on the given test task.
	 * Only tasks of type [Test] will be accepted.
	 */
	fun executionData(test: Task) {
		executionData(test.project.tasks.named(test.name))
	}

	/**
	 * Sets the execution data, classDirectories and partial flag based on the given test task.
	 * Only tasks of type [Test] will be accepted.
	 */
	fun executionData(test: TaskProvider<out Task>) {
		partial.set(test.map {
			check(it is Test) { "executionData of TestwiseCoverageReport expected a Test task as input" }
			it.teamscale.partial.get()
		})
		executionData.from(test.map {
			check(it is Test) { "executionData of TestwiseCoverageReport expected a Test task as input" }
			it.teamscale.agent.destination
		})
		classDirectories.convention(test.map {
			check(it is Test) { "executionData of TestwiseCoverageReport expected a Test task as input" }
			it.classpath
		})
		mustRunAfter(test)
	}
}
