package com.teamscale.client

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.*
import java.util.regex.Pattern

/**
 * Routes every request through the given [proxy], except requests to hosts that match [nonProxyHosts].
 *
 * [nonProxyHosts] is interpreted like the JVM's `http.nonProxyHosts` (see `sun.net.spi.DefaultProxySelector`):
 * `|`-separated, case-insensitive host patterns, each with an optional `*` wildcard at its start and/or end. The
 * JVM's default exclusions ([JVM_DEFAULT_NON_PROXY_HOSTS]) apply if [nonProxyHosts] is `null` and are added to any
 * non-empty value. An empty value excludes no host.
 */
internal class NonProxyHostsProxySelector(private val proxy: Proxy, nonProxyHosts: String?) : ProxySelector() {

	private val nonProxyHostsPattern = toPattern(
		when {
			nonProxyHosts == null -> JVM_DEFAULT_NON_PROXY_HOSTS
			nonProxyHosts.isEmpty() -> nonProxyHosts
			else -> "$nonProxyHosts|$JVM_DEFAULT_NON_PROXY_HOSTS"
		}
	)

	override fun select(uri: URI): List<Proxy> {
		val host = uri.host?.lowercase(Locale.ROOT)
		if (host != null && nonProxyHostsPattern?.matcher(host)?.matches() == true) {
			return listOf(Proxy.NO_PROXY)
		}
		return listOf(proxy)
	}

	override fun connectFailed(uri: URI?, address: SocketAddress?, exception: IOException?) {
		// Nothing to do, there is no alternative proxy to fall back to
	}

	companion object {
		/** The hosts the JVM never proxies, unless `http.nonProxyHosts` is set to an empty value. */
		const val JVM_DEFAULT_NON_PROXY_HOSTS = "localhost|127.*|[::1]|0.0.0.0|[::0]"

		/** Returns the pattern that matches the given host patterns, or `null` if there are none. */
		private fun toPattern(hostPatterns: String): Pattern? {
			val regexes = hostPatterns.split("|")
				.filter { it.isNotEmpty() }
				.map { toRegex(it.lowercase(Locale.ROOT)) }
			if (regexes.isEmpty()) {
				return null
			}
			return Pattern.compile(regexes.joinToString("|"))
		}

		private fun toRegex(hostPattern: String): String {
			if (hostPattern == "*") {
				return ".*"
			}
			val prefix = if (hostPattern.startsWith("*")) ".*" else ""
			val suffix = if (hostPattern.endsWith("*")) ".*" else ""
			val literal = hostPattern.removePrefix("*").removeSuffix("*")
			return prefix + Pattern.quote(literal) + suffix
		}
	}
}
