package com.teamscale.tia.client

/** Utils for URL encoding as the Java internal URLEncoder does not handle all escape-worthy symbols for path segments.  */
object UrlUtils {
	/**
	 * Percent-decodes a string, such as used in a URL Path (not a query string / form encode, which uses + for spaces,
	 * etc).
	 * Source: https://stackoverflow.com/a/44076794
	 */
	fun String.encodeUrl() =
		replace("%", "%25")
			.replace(" ", "%20")
			.replace("!", "%21")
			.replace("#", "%23")
			.replace("$", "%24")
			.replace("&", "%26")
			.replace("'", "%27")
			.replace("(", "%28")
			.replace(")", "%29")
			.replace("*", "%2A")
			.replace("+", "%2B")
			.replace(",", "%2C")
			.replace("/", "%2F")
			.replace(":", "%3A")
			.replace(";", "%3B")
			.replace("=", "%3D")
			.replace("?", "%3F")
			.replace("@", "%40")
			.replace("[", "%5B")
			.replace("]", "%5D")
}
