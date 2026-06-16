package com.teamscale.report

import com.fasterxml.jackson.core.JsonProcessingException
import com.teamscale.client.FileSystemUtils
import com.teamscale.client.JsonUtils
import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.client.TestDetails
import com.teamscale.report.testwise.ETestArtifactFormat
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import java.io.File
import java.io.IOException

/** Utilities for generating reports.  */
object ReportUtils {
	/** Converts to given test list to a json report and writes it to the given file.  */
	@Throws(IOException::class)
	fun writeTestListReport(reportFile: File, report: List<TestDetails>) {
		writeReportToFile(reportFile, report)
	}

	/** Converts to given test execution report to a json report and writes it to the given file.  */
	@Throws(IOException::class)
	fun writeTestExecutionReport(reportFile: File, report: List<TestExecution>) {
		writeReportToFile(reportFile, report)
	}

	/** Converts to given testwise coverage report to a json report and writes it to the given file.  */
	@Throws(IOException::class)
	fun writeTestwiseCoverageReport(reportFile: File, report: TestwiseCoverageReport) {
		writeReportToFile(reportFile, report)
	}

	/** Converts to given report to a json string. For testing only.  */
	@Throws(JsonProcessingException::class)
	fun getTestwiseCoverageReportAsString(report: TestwiseCoverageReport) = report.serializeToJson()

	/** Writes the report object to the given file as json.  */
	@Throws(IOException::class)
	private fun <T> writeReportToFile(reportFile: File, report: T) {
		val directory = reportFile.getParentFile()
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw IOException(
				"Failed to create the report output directory '${directory.absolutePath}'." +
						" Verify the parent directory exists and is writable."
			)
		}
		JsonUtils.serializeToFile(reportFile, report)
	}

	/** Recursively lists all files in the given directory that match the specified extension.  */
	@Throws(IOException::class)
	@JvmStatic
	fun <T> readObjects(
		format: ETestArtifactFormat,
		clazz: Class<Array<T>>,
		directoriesOrFiles: Collection<File>
	) = listFiles(format, directoriesOrFiles)
		.map { JsonUtils.deserializeFile(it, clazz) }
		.flatMap { listOf(*it) }

	/** Recursively lists all files of the given artifact type.  */
	@JvmStatic
	fun listFiles(
		format: ETestArtifactFormat,
		directoriesOrFiles: Collection<File>
	) = directoriesOrFiles.flatMap { directoryOrFile ->
		when {
			directoryOrFile.isDirectory() -> {
				directoryOrFile.walkTopDown().filter { it.isOfArtifactFormat(format) }.toList()
			}

			directoryOrFile.isOfArtifactFormat(format) -> {
				listOf(directoryOrFile)
			}

			else -> emptyList()
		}
	}

	private fun File.isOfArtifactFormat(format: ETestArtifactFormat) =
		isFile() &&
				getName().startsWith(format.filePrefix) &&
				FileSystemUtils.getFileExtension(this).equals(format.extension, ignoreCase = true)
}
