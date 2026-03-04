package com.teamscale

import com.teamscale.aggregation.junit.JUnitReportCollectionTask
import com.teamscale.client.EReportFormat
import com.teamscale.client.TeamscaleClient
import com.teamscale.config.ServerConfiguration
import com.teamscale.config.internal.CommitInfo
import com.teamscale.reporting.compact.CompactCoverageReport
import com.teamscale.reporting.testwise.TestwiseCoverageReport
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val MAX_RETRY_COUNT = 3

/** Handles report uploads to Teamscale. */
@DisableCachingByDefault(because = "Uploads should always be executed")
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class TeamscaleUpload : DefaultTask() {

	/** The partition to upload the report to. */
	@get:Input
	abstract val partition: Property<String>

	/** The commit message shown in Teamscale for the upload. */
	@get:Input
	abstract val message: Property<String>

	/** Whether the task should fail when the upload failed (default false). */
	@get:Input
	abstract val ignoreFailures: Property<Boolean>

	/** The Teamscale server configuration. */
	@get:Input
	internal abstract val serverConfiguration: Property<ServerConfiguration>

	/** The commit/revision for which the reports should be uploaded. */
	@get:Input
	@get:Optional
	internal abstract val commitDescriptorOrRevision: Property<CommitInfo>

	/**
	 * The repository id in your Teamscale project which Teamscale should use to look up the revision, if given.
	 * Null or empty will lead to a lookup in all repositories in the Teamscale project.
	 */
	@get:Input
	@get:Optional
	internal abstract val repository: Property<String>

	/** The list of reports to be uploaded. */
	@get:Input
	internal abstract val reports: MapProperty<String, ConfigurableFileCollection>

	/** Object factory. */
	@get:Inject
	protected abstract val objectFactory: ObjectFactory

	init {
		group = "Teamscale"
		description = "Uploads reports to Teamscale"
	}

	/**
	 * Adds the report produced by the given task to the upload and adds a dependency on the task.
	 * @param task Supports [Test], [JacocoReport], [CompactCoverageReport], [TestwiseCoverageReport] and [JUnitReportCollectionTask]
	 */
	fun from(task: TaskProvider<*>) {
		from(task.get())
	}

	/**
	 * Adds the report produced by the given task to the upload and adds a dependency on the task.
	 * @param task Supports [Test], [JacocoReport], [CompactCoverageReport], [TestwiseCoverageReport] and [JUnitReportCollectionTask]
	 */
	fun from(task: Task) {
		when (task) {

			is Test -> {
				mustRunAfter(task)
				check(task.reports.junitXml.required.get()) { "XML report generation is not enabled for task ${task.path}! Enable it by setting reports.junitXml.required = true for the task, to be able to upload it." }
				addReport(EReportFormat.JUNIT.name, task.reports.junitXml.outputLocation.asFileTree.matching {
					include("**/*.xml")
				})
			}

			is JacocoReport -> {
				dependsOn(task)
				check(task.reports.xml.required.get()) { "XML report generation is not enabled for task ${task.path}! Enable it by setting reports.xml.required = true for the task, to be able to upload it." }
				addReport(EReportFormat.JACOCO.name, task.reports.xml.outputLocation)
			}

			is CompactCoverageReport -> {
				dependsOn(task)
				check(task.reports.compactCoverage.required.get()) { "Compact coverage report generation is not enabled for task ${task.path}! Enable it by setting reports.compactCoverageReport.required = true for the task, to be able to upload it." }
				addReport(
					EReportFormat.TEAMSCALE_COMPACT_COVERAGE.name,
					task.reports.compactCoverage.outputLocation
				)
			}

			is TestwiseCoverageReport -> {
				dependsOn(task)
				check(task.reports.testwiseCoverage.required.get()) { "Testwise coverage report generation is not enabled for task ${task.path}! Enable it by setting reports.testwiseCoverage.required = true for the task, to be able to upload it." }
				addReport(EReportFormat.TESTWISE_COVERAGE.name, task.reports.testwiseCoverage.outputLocation)
			}

			is JUnitReportCollectionTask -> {
				dependsOn(task)
				addReport(EReportFormat.JUNIT.name, task.project.provider { task.destinationDir })
			}

			else -> throw GradleException("Unsupported task type ${task.javaClass.name}! Use addReport(format, reportFiles) instead to upload reports produced by other tasks.")
		}
	}

	/**
	 * Adds arbitrary report files to be uploaded.
	 *
	 * @param format Report format (see EReportFormat#name for valid values)
	 * @param reportFiles Any objects that are accepted by [org.gradle.api.Project.files]
	 */
	fun addReport(format: String, reportFiles: Any) {
		val files = reports.getting(format).orNull
		if (files == null) {
			reports.put(format, project.files(reportFiles))
		} else {
			files.from(reportFiles)
		}
	}

	/** Executes the report upload. */
	@TaskAction
	fun action() {
		val reports = reports.get()

		if (reports.isEmpty()) {
			logger.warn("Skipping upload. No reports specified for uploading.")
			return
		}

		val server = serverConfiguration.get()
		server.validate()
		check(commitDescriptorOrRevision.isPresent) { "To upload to Teamscale you must specify the commit to which the data should be uploaded to via teamscale.commit (revision or branchName/timestamp)!" }

		try {
			logger.info("Uploading to ${server.url.get()} at ${commitDescriptorOrRevision.get()}...")
			server.toClient().uploadReports(reports)
		} catch (e: Exception) {
			if (ignoreFailures.get()) {
				logger.warn("Ignoring failure during upload:")
				logger.warn(e.message, e)
			} else {
				throw e
			}
		}
	}

	private fun TeamscaleClient.uploadReports(reports: MutableMap<String, ConfigurableFileCollection>) {
		val formatAndReports = reports.mapValues { getExistingReportFiles(it.value) }.filter {
			if (it.value.isEmpty()) {
				logger.info("Skipped empty upload for ${it.key} reports to partition ${partition.get()}.")
				false
			} else {
				true
			}
		}
		logger.info("Uploading ${formatAndReports.values.flatten().size} report(s) to partition ${partition.get()}...")

		formatAndReports.forEach { (format, files) ->
			files.forEach {
				logger.debug("- {}: {}", format, it.absolutePath)
			}
		}

		uploadReportFiles(formatAndReports, partition.get(), message.get())
	}

	private fun TeamscaleClient.uploadReportFiles(
		reports: Map<String, Collection<File>>,
		partition: String,
		message: String
	) {
		try {
			retry(MAX_RETRY_COUNT) {
				uploadReports(
					reports,
					commitDescriptorOrRevision.get().commit,
					commitDescriptorOrRevision.get().revision,
					repository.orNull,
					partition,
					message
				)
			}
		} catch (e: IOException) {
			throw GradleException("Upload failed (${e.message})", e)
		}
	}

	/**
	 * Retries the given block numOfRetries-times catching any thrown exceptions.
	 * If none of the retries succeeded, the latest caught exception is rethrown.
	 */
	private fun <T> retry(numOfRetries: Int, block: () -> T): T {
		var throwable: Throwable? = null
		(1..numOfRetries).forEach { attempt ->
			try {
				return block()
			} catch (e: Throwable) {
				throwable = e
				println("Failed attempt $attempt / $numOfRetries")
			}
		}
		throw throwable!!
	}

	private fun getExistingReportFiles(reports: FileCollection) =
		reports.files.filter { it.exists() }.flatMap { fileOrDir -> fileOrDir.walkTopDown().filter { it.isFile } }
			.distinct()
}
