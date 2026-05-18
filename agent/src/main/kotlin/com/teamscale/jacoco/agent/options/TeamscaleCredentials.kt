package com.teamscale.jacoco.agent.options

import okhttp3.HttpUrl

/** Credentials for accessing a Teamscale instance.  */
class TeamscaleCredentials(
	/** The URL of the Teamscale server.  */
	@JvmField val url: HttpUrl?,
	/** The user name used to authenticate against Teamscale.  */
	@JvmField val userName: String?,
	/** The user's access key.  */
	@JvmField val accessKey: String?
)
