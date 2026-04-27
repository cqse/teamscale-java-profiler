package com.teamscale.jacoco.agent.upload.azure

import com.teamscale.jacoco.agent.options.AgentOptionParseException
import com.teamscale.jacoco.agent.options.AgentOptionsParser
import okhttp3.HttpUrl

/** Config necessary to upload files to an azure file storage.  */
class AzureFileStorageConfig {
	/** The URL to the azure file storage  */
	@JvmField
	var url: HttpUrl? = null

	/** The access key of the azure file storage  */
	@JvmField
	var accessKey: String? = null

	/** Checks if none of the required fields is null.  */
	fun hasAllRequiredFieldsSet(): Boolean {
		return url != null && accessKey != null
	}

	/** Checks if all required fields are null.  */
	fun hasAllRequiredFieldsNull(): Boolean {
		return url == null && accessKey == null
	}

	companion object {
		/**
		 * Handles all command-line options prefixed with 'azure-'
		 * @return true if it has successfully processed the given option.
		 */
		@JvmStatic
		@Throws(AgentOptionParseException::class)
		fun handleAzureFileStorageOptions(
			azureFileStorageConfig: AzureFileStorageConfig, key: String,
			value: String
		): Boolean {
			when (key) {
				"azure-url" -> {
					azureFileStorageConfig.url = AgentOptionsParser.parseUrl(key, value)
					return true
				}
				"azure-key" -> {
					azureFileStorageConfig.accessKey = value
					return true
				}
				else -> return false
			}
		}
	}
}
