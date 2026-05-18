package com.teamscale.jacoco.agent.upload.azure

/** Constants for the names of HTTP header used in a request to an Azure file storage.  */ /* package */
internal object AzureHttpHeader {
	/** Same as [.CONTENT_LENGTH]  */ /* package */
	const val X_MS_CONTENT_LENGTH: String = "x-ms-content-length"

	/** Same as [.DATE]  */ /* package */
	const val X_MS_DATE: String = "x-ms-date"

	/** Same as [.RANGE]  */ /* package */
	const val X_MS_RANGE: String = "x-ms-range"

	/** Type of filesystem object which the request is referring to. Can be 'file' or 'directory'.  */ /* package */
	const val X_MS_TYPE: String = "x-ms-type"

	/** Version of the Azure file storage API  */ /* package */
	const val X_MS_VERSION: String = "x-ms-version"

	/**
	 * Defines the type of write operation on a file. Can either be 'Update' or 'Clear'. For 'Update' the 'Range' and
	 * 'Content-Length' headers must match, for 'Clear', 'Content-Length' must be set to 0.
	 */
	/* package */
	const val X_MS_WRITE: String = "x-ms-write"

	/**
	 * Defines the authorization and must contain the account name and signature. Must be given in the following format:
	 * Authorization="[SharedKey|SharedKeyLite] <AccountName>:<Signature>"
	</Signature></AccountName> */
	/* package */
	const val AUTHORIZATION: String = "Authorization"

	/** Content-Encoding  */ /* package */
	const val CONTENT_ENCODING: String = "Content-Encoding"

	/** Content-Language  */ /* package */
	const val CONTENT_LANGUAGE: String = "Content-Language"

	/** Content-Length  */ /* package */
	const val CONTENT_LENGTH: String = "Content-Length"

	/** The md5 hash of the sent content.  */ /* package */
	const val CONTENT_MD_5: String = "Content-MD5"

	/** Content-Type  */ /* package */
	const val CONTENT_TYPE: String = "Content-Type"

	/** The date time of the request  */ /* package */
	const val DATE: String = "Date"

	/** Only send the response if the entity has not been modified since a specific time.  */ /* package */
	const val IF_UNMODIFIED_SINCE: String = "If-Unmodified-Since"

	/** Allows a 304 Not Modified to be returned if content is unchanged.  */ /* package */
	const val IF_MODIFIED_SINCE: String = "If-Modified-Since"

	/**
	 * Only perform the action if the client supplied entity matches the same entity on the server. This is mainly for
	 * methods like PUT to only update a resource if it has not been modified since the user last updated it.
	 */
	/* package */
	const val IF_MATCH: String = "If-Match"

	/** Allows a 304 Not Modified to be returned if content is unchanged  */ /* package */
	const val IF_NONE_MATCH: String = "If-None-Match"

	/**
	 * Specifies the range of bytes to be written. Both the start and end of the range must be specified. Must be given
	 * in the following format: "bytes=startByte-endByte"
	 */
	/* package */
	const val RANGE: String = "Range"
}
