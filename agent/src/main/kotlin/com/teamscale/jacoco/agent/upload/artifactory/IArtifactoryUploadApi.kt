package com.teamscale.jacoco.agent.upload.artifactory

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.File
import java.io.IOException

/** [retrofit2.Retrofit] API specification for the [ArtifactoryUploader].  */
interface IArtifactoryUploadApi {
	/** The upload API call.  */
	@PUT("{path}")
	fun upload(@Path("path") path: String, @Body uploadedFile: RequestBody): Call<ResponseBody>

	/**
	 * Convenience method to perform an upload for a coverage zip.
	 */
	@Throws(IOException::class)
	fun uploadCoverageZip(path: String, data: File): Response<ResponseBody> {
		val body = data.asRequestBody("application/zip".toMediaTypeOrNull())
		return upload(path, body).execute()
	}
}
