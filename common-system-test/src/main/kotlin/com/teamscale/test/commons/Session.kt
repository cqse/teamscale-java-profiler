package com.teamscale.test.commons

import com.fasterxml.jackson.core.JsonProcessingException
import com.teamscale.client.EReportFormat
import com.teamscale.client.JsonUtils
import com.teamscale.report.compact.TeamscaleCompactCoverageReport
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import spark.Request
import java.io.IOException

/**
 * Represents an upload session to Teamscale, which contains data from a single partition, for a specific
 * commit/revision. A session may contain data in multiple formats.
 */
class Session(request: Request) {
	/** Returns the partition name for which the session was opened.  */
	val partition: String? = request.queryParams("partition")
	private val revision: String? = request.queryParams("revision")
	private val commit: String? = request.queryParams("t")
	private val repository: String? = request.queryParams("repository")

	/** Whether the session was committed.  */
	var isCommitted: Boolean = false
		private set

	private val reports = mutableMapOf<EReportFormat, MutableList<ExternalReport>>()

	/** Returns the revision for which the session was opened. */
	fun getRevision() = revision

	/**
	 * Retrieves the commit information for the session. Combines the revision and commit data into a single string
	 * representation separated by a comma.
	 */
	fun getCommit() = "$revision:$repository, $commit"

	/**
	 * Marks the session as committed, which means no more data will be added, and a real Teamscale would start
	 * processing the data now.
	 */
	fun markCommitted() {
		isCommitted = true
	}

	/** Adds a new report into the session.  */
	fun addReport(format: String, report: String) {
		reports.getOrPut(EReportFormat.valueOf(format)) { mutableListOf() }.add(ExternalReport(report))
	}

	/** Returns all reports from this session.  */
	fun getReports(): List<ExternalReport> = reports.values.flatten()

	/** Returns all reports of the given format.  */
	fun getReports(format: EReportFormat): MutableList<ExternalReport>? = reports[format]

	/** Returns the only report in the given format. It asserts that there are no other reports present.  */
	fun getOnlyReport(format: EReportFormat): String {
		check(reports.keys.size == 1) {
			"Expected exactly one report format, but got ${reports.keys}!"
		}
		check(reports.contains(format)) {
			"No ${format.readableName} report found! Session contains ${
				reports.keys.map(EReportFormat::readableName)
			} reports."
		}
		check(reports[format]?.size == 1) {
			"Expected exactly one ${format.readableName} report, but got ${reports[format]?.size}."
		}
		return reports[format]!!.first().reportString
	}

	@get:Throws(JsonProcessingException::class)
	val onlyTestwiseCoverageReport: TestwiseCoverageReport
		/** Returns the only Testwise Coverage report in deserialized form.  */
		get() = JsonUtils.deserialize<TestwiseCoverageReport>(
			getOnlyReport(EReportFormat.TESTWISE_COVERAGE)
		)

	/**
	 * Returns the report at the given index in [reports], parsed as a [TeamscaleCompactCoverageReport].
	 *
	 * @throws IOException when parsing the report fails.
	 */
	@Throws(IOException::class)
	fun getCompactCoverageReport(index: Int) =
		reports[EReportFormat.TEAMSCALE_COMPACT_COVERAGE]?.getOrNull(index)?.let {
			JsonUtils.deserialize<TeamscaleCompactCoverageReport>(it.reportString)
		}
}
