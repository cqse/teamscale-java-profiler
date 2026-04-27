package com.teamscale.jacoco.agent.upload.azure

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

/** [retrofit2.Retrofit] API specification for the [AzureFileStorageUploader].  */
interface IAzureUploadApi {
	/** PUT call to the azure file storage without any data in the body  */
	@PUT("{path}")
	fun put(
		@Path(value = "path", encoded = true) path: String?,
		@HeaderMap headers: MutableMap<String, String>?,
		@QueryMap query: MutableMap<String, String>?
	): Call<ResponseBody>

	/** PUT call to the azure file storage with data in the body  */
	@PUT("{path}")
	fun putData(
		@Path(value = "path", encoded = true) path: String?,
		@HeaderMap headers: MutableMap<String, String>?,
		@QueryMap query: MutableMap<String, String>?,
		@Body content: RequestBody?
	): Call<ResponseBody>

	/** HEAD call to the azure file storage  */
	@HEAD("{path}")
	fun head(
		@Path(value = "path", encoded = true) path: String?,
		@HeaderMap headers: MutableMap<String, String>?,
		@QueryMap query: MutableMap<String, String>?
	): Call<Void>
}
