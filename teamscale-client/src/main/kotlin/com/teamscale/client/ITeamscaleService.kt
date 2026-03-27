package com.teamscale.client

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.IOException

/** [Retrofit] API specification for Teamscale. */
interface ITeamscaleService {

	/**
	 * Report upload API.
	 *
	 * @param commit           A branch and timestamp to upload the report to. Can be null if revision is specified.
	 * @param revision         This parameter allows passing a revision instead of a timestamp. Can be null if a
	 *                         timestamp is given.
	 * @param partition        The name of the logical partition to store the results into. All existing data in this
	 *                         partition will be invalidated. A partition typically corresponds to one analysis run,
	 *                         i.e., if there are two independent builds/runs, they must use different partitions.
	 * @apiNote [How to Upload External Analysis Results to Teamscale](https://docs.teamscale.com/howto/uploading-external-results/#upload-via-command-line)
	 * for details.
	 */
	@Multipart
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session/auto-create/report")
	fun uploadExternalReport(
		@Path("projectId") projectId: String,
		@Query("format") format: String,
		@Query("t") commit: CommitDescriptor?,
		@Query("revision") revision: String?,
		@Query("repository") repository: String?,
		@Query("partition") partition: String,
		@Query("message") message: String,
		@Part("report") report: RequestBody
	): Call<ResponseBody>

	/**
	 * Add multiple files into an existing session.
	 */
	@Multipart
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session/{sessionId}/report")
	fun uploadExternalReports(
		@Path("projectId") projectId: String,
		@Path("sessionId") sessionId: String,
		@Query("format") format: String,
		@Part report: List<MultipartBody.Part>
	): Call<ResponseBody>

	/** Uploads reports in an auto-create session. */
	@Multipart
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session/auto-create/report")
	fun uploadExternalReports(
		@Path("projectId") projectId: String,
		@Query("format") format: EReportFormat,
		@Query("t") commit: CommitDescriptor?,
		@Query("revision") revision: String?,
		@Query("repository") repository: String?,
		@Query("partition") partition: String,
		@Query("message") message: String,
		@Part report: List<MultipartBody.Part>
	): Call<ResponseBody>

	/** Creates an external report session, which allows to upload reports in multiple formats as one commit. */
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session")
	fun createSession(
		@Path("projectId") projectId: String,
		@Query("t") commit: CommitDescriptor?,
		@Query("revision") revision: String?,
		@Query("repository") repository: String?,
		@Query("partition") partition: String,
		@Query("message") message: String
	): Call<String>

	/** Commits/closes the given session. */
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session/{sessionId}")
	fun commitSession(
		@Path("projectId") projectId: String,
		@Path("sessionId") sessionId: String
	): Call<Void>

	/**
	 * Report upload API for multiple reports at once. This is an overloaded version that takes a string as report
	 * format so that consumers can add support for new report formats without requiring changes to teamscale-client.
	 *
	 * @see uploadExternalReport
	 */
	@Multipart
	@POST("api/v2024.7.0/projects/{projectId}/external-analysis/session/auto-create/report")
	fun uploadExternalReports(
		@Path("projectId") projectId: String,
		@Query("format") format: String,
		@Query("t") commit: CommitDescriptor?,
		@Query("revision") revision: String?,
		@Query("repository") repository: String?,
		@Query("partition") partition: String,
		@Query("message") message: String,
		@Part report: List<MultipartBody.Part>
	): Call<ResponseBody>

	/** Retrieve clustered impacted tests based on the given available tests and baseline timestamp. */
	@PUT("api/v2024.7.0/projects/{projectId}/impacted-tests")
	fun getImpactedTests(
		@Path("projectId") projectId: String,
		@Query("baseline") baseline: String?,
		@Query("baseline-revision") baselineRevision: String?,
		@Query("end") end: CommitDescriptor?,
		@Query("end-revision") endRevision: String?,
		@Query("repository") repository: String?,
		@Query("partitions") partitions: List<String>,
		@Query("include-non-impacted") includeNonImpacted: Boolean,
		@Query("include-failed-and-skipped") includeFailedAndSkippedTests: Boolean,
		@Query("ensure-processed") ensureProcessed: Boolean,
		@Query("include-added-tests") includeAddedTests: Boolean,
		@Body availableTests: List<TestWithClusterId>
	): Call<List<PrioritizableTestCluster>>

	/** Retrieve unclustered impacted tests based on all tests known to Teamscale and the given baseline timestamp. */
	@GET("api/v2024.7.0/projects/{projectId}/impacted-tests")
	fun getImpactedTests(
		@Path("projectId") projectId: String,
		@Query("baseline") baseline: String?,
		@Query("baseline-revision") baselineRevision: String?,
		@Query("end") end: CommitDescriptor?,
		@Query("end-revision") endRevision: String?,
		@Query("repository") repository: String?,
		@Query("partitions") partitions: List<String>,
		@Query("include-non-impacted") includeNonImpacted: Boolean,
		@Query("include-failed-and-skipped") includeFailedAndSkippedTests: Boolean,
		@Query("ensure-processed") ensureProcessed: Boolean,
		@Query("include-added-tests") includeAddedTests: Boolean
	): Call<List<PrioritizableTest>>

	/** Removes the profiler identified by given ID.  */
	@Deprecated("This is here for compatibility with older Teamscale version; remove after these are no longer supported.")
	@DELETE("api/v2024.7.0/running-profilers/{profilerId}")
	fun unregisterProfilerLegacy(@Path("profilerId") profilerId: String?): Call<ResponseBody>

	/** Registers a profiler to Teamscale and returns the profiler configuration it should be started with.  */
	@POST("api/v2024.7.0/profilers")
	fun registerProfiler(
		@Query("configuration-id") configurationId: String?,
		@Body processInformation: ProcessInformation?
	): Call<ResponseBody>

	/** Updates the profiler infos and sets the profiler to still alive.  */
	@PUT("api/v2024.7.0/profilers/{profilerId}")
	fun sendHeartbeat(
		@Path("profilerId") profilerId: String,
		@Body profilerInfo: ProfilerInfo?
	): Call<ResponseBody>

	/** Removes the profiler identified by given ID.  */
	@DELETE("api/v2024.7.0/profilers/{profilerId}")
	fun unregisterProfiler(@Path("profilerId") profilerId: String): Call<ResponseBody>

	/** Send logs to Teamscale  */
	@POST("api/v2024.7.0/profilers/{profilerId}/logs")
	fun postProfilerLog(
		@Path("profilerId") profilerId: String,
		@Body logEntries: List<ProfilerLogEntry>?
	): Call<Void>
}

/**
 * Uploads the given report body to Teamscale as blocking call.
 *
 * @return Returns the request body if successful, otherwise throws an IOException.
 */
@Throws(IOException::class)
fun ITeamscaleService.uploadReport(
	projectId: String,
	commit: CommitDescriptor?,
	revision: String?,
	repository: String?,
	partition: String,
	reportFormat: EReportFormat,
	message: String,
	report: RequestBody
): String {
	var commitNull = commit
	if (!revision.isNullOrBlank()) {
		// When uploading to a revision, we don't need commit adjustment.
		commitNull = null
	}

	try {
		val response = uploadExternalReport(
			projectId, reportFormat.name, commitNull, revision, repository, partition, message, report
		).execute()

		val body = response.body()
		if (response.isSuccessful) {
			return body?.string() ?: ""
		}

		val errorBody = HttpUtils.getErrorBodyStringSafe(response)
		throw IOException("Request failed with error code ${response.code()}. Response body: $errorBody")
	} catch (e: IOException) {
		throw IOException("Failed to upload report. ${e.message}", e)
	}
}
