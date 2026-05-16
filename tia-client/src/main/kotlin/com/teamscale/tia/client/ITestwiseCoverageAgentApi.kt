package com.teamscale.tia.client

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.report.testwise.model.TestExecution
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/** [Retrofit] API specification for the JaCoCo agent in test-wise coverage mode.  */
interface ITestwiseCoverageAgentApi {
	/** Set the partition name as shown in Teamscale.  */
	@PUT("partition")
	fun setPartition(@Body partition: String): Call<ResponseBody>

	/** Set the revision as shown in Teamscale.  */
	@PUT("revision")
	fun setRevision(@Body partition: String): Call<ResponseBody>

	/** Set the upload commit as shown in Teamscale.  */
	@PUT("commit")
	fun setCommit(@Body commit: String): Call<ResponseBody>

	/** Set the commit message with which the upload is shown in Teamscale.  */
	@PUT("message")
	fun setMessage(@Body message: String): Call<ResponseBody>

	/** Test start.  */
	@POST("test/start/{testUniformPath}")
	fun testStarted(@Path(value = "testUniformPath", encoded = true) testUniformPath: String): Call<ResponseBody>

	/** Test finished.  */
	@POST("test/end/{testUniformPath}")
	fun testFinished(
		@Path(value = "testUniformPath", encoded = true) testUniformPath: String
	): Call<ResponseBody>

	/** Test finished.  */
	@POST("test/end/{testUniformPath}")
	fun testFinished(
		@Path(value = "testUniformPath", encoded = true) testUniformPath: String,
		@Body testExecution: TestExecution
	): Call<ResponseBody>

	/**
	 * Test run started. Returns a single dummy cluster of TIA-selected and -prioritized tests
	 * that Teamscale currently knows about.
	 */
	@POST("testrun/start")
	fun testRunStarted(
		@Query("include-non-impacted") includeNonImpacted: Boolean,
		@Query("baseline") baseline: Long?,
		@Query("baseline-revision") baselineRevision: String?
	): Call<List<PrioritizableTestCluster>>

	/**
	 * Test run started. Returns the list of TIA-selected and -prioritized test clusters to execute.
	 */
	@POST("testrun/start")
	fun testRunStarted(
		@Query("include-non-impacted") includeNonImpacted: Boolean,
		@Query("baseline") baseline: Long?,
		@Query("baseline-revision") baselineRevision: String?,
		@Body availableTests: List<ClusteredTestDetails>
	): Call<List<PrioritizableTestCluster>>

	/**
	 * Test run finished. Generate a test-wise coverage report and upload to Teamscale.
	 *
	 * @param partial Whether the test recording only contains a subset of the available tests.
	 */
	@POST("testrun/end")
	fun testRunFinished(@Query("partial") partial: Boolean): Call<ResponseBody>

	companion object {
		/**
		 * Generates a [Retrofit] instance for this service, which uses basic auth to authenticate against the server
		 * and which sets the Accept header to JSON.
		 */
		fun createService(baseUrl: HttpUrl): ITestwiseCoverageAgentApi {
			val httpClientBuilder = OkHttpClient.Builder().apply {
				connectTimeout(60, TimeUnit.SECONDS)
				readTimeout(120, TimeUnit.SECONDS)
				writeTimeout(60, TimeUnit.SECONDS)
			}
			return Retrofit.Builder()
				.client(httpClientBuilder.build())
				.baseUrl(baseUrl)
				.addConverterFactory(JacksonConverterFactory.create())
				.build()
				.create(ITestwiseCoverageAgentApi::class.java)
		}
	}
}
