package com.teamscale.client

/**
 * Manages Java system properties for:
 * - http.proxyHost
 * - http.proxyPort
 * - http.proxyUser
 * - http.proxyPassword
 * - http.nonProxyHosts
 *
 * Or their HTTPS counterparts (https.*), except for http.nonProxyHosts, which the JVM uses for both protocols.
 * These values set the proxy server and credentials used to reach Teamscale, and the hosts that are reached without
 * the proxy.
 */
open class ProxySystemProperties(private val protocol: Protocol) {

	companion object {
		private const val PROXY_HOST_SYSTEM_PROPERTY = ".proxyHost"
		private const val PROXY_PORT_SYSTEM_PROPERTY = ".proxyPort"
		private const val PROXY_USER_SYSTEM_PROPERTY = ".proxyUser"
		private const val PROXY_PASSWORD_SYSTEM_PROPERTY = ".proxyPassword"
		private const val NON_PROXY_HOSTS_SYSTEM_PROPERTY = ".nonProxyHosts"

		/** The JVM's exclusion list, which the JVM applies to both HTTP and HTTPS. */
		private const val JVM_NON_PROXY_HOSTS_SYSTEM_PROPERTY = "http.nonProxyHosts"
	}

	/**
	 * Enum representing the supported protocols.
	 */
	enum class Protocol {
		HTTP, HTTPS;

		/**
		 * Returns the protocol name in lowercase.
		 */
		override fun toString() = name.lowercase()
	}

	/**
	 * Prefix for the system property keys.
	 * Can be overridden by subclasses to provide a different prefix.
	 */
	protected open val propertyPrefix = ""

	/**
	 * The proxy host system property.
	 */
	var proxyHost: String?
		get() = getProperty(PROXY_HOST_SYSTEM_PROPERTY)
		set(value) {
			setProperty(PROXY_HOST_SYSTEM_PROPERTY, value)
		}

	/**
	 * The proxy port system property.
	 * Must be a positive integer and less than or equal to 65535.
	 */
	var proxyPort: Int
		get() = getProperty(PROXY_PORT_SYSTEM_PROPERTY)?.toIntOrNull() ?: -1
		set(value) {
			check(value > 0) { "Port must be a positive integer" }
			check(value <= 65535) { "Port must be less than or equal to 65535" }
			setProperty(PROXY_PORT_SYSTEM_PROPERTY, value.toString())
		}

	/**
	 * The proxy user system property.
	 */
	var proxyUser: String?
		get() = getProperty(PROXY_USER_SYSTEM_PROPERTY)
		set(value) {
			setProperty(PROXY_USER_SYSTEM_PROPERTY, value)
		}

	/**
	 * The proxy password system property.
	 */
	var proxyPassword: String?
		get() = getProperty(PROXY_PASSWORD_SYSTEM_PROPERTY)
		set(value) {
			setProperty(PROXY_PASSWORD_SYSTEM_PROPERTY, value)
		}

	/**
	 * The hosts that are reached without the proxy, in the format of the JVM's `http.nonProxyHosts`. Without a
	 * [propertyPrefix], this is `http.nonProxyHosts` for both protocols, like in the JVM. With a prefix, it is
	 * protocol-specific (e.g. `teamscale.https.nonProxyHosts`) and falls back to `http.nonProxyHosts`.
	 */
	var nonProxyHosts: String?
		get() = System.getProperty(nonProxyHostsKey) ?: System.getProperty(JVM_NON_PROXY_HOSTS_SYSTEM_PROPERTY)
		set(value) {
			setSystemProperty(nonProxyHostsKey, value)
		}

	private val nonProxyHostsKey: String
		get() = if (propertyPrefix.isEmpty()) {
			JVM_NON_PROXY_HOSTS_SYSTEM_PROPERTY
		} else {
			key(NON_PROXY_HOSTS_SYSTEM_PROPERTY)
		}

	/**
	 * Returns the full system property key, e.g. `teamscale.https.proxyHost`, for the given property suffix.
	 */
	private fun key(property: String) = "$propertyPrefix$protocol$property"

	/**
	 * Retrieves the system property value for the given property key.
	 *
	 * @param property The property key.
	 * @return The property value or null if not set.
	 */
	private fun getProperty(property: String): String? = System.getProperty(key(property))

	/**
	 * Sets the system property value for the given property key.
	 *
	 * @param property The property key.
	 * @param value The property value to set.
	 */
	private fun setProperty(property: String, value: String?) = setSystemProperty(key(property), value)

	/**
	 * Sets the system property with the given full key, unless the value is `null`.
	 */
	private fun setSystemProperty(key: String, value: String?) {
		value?.let {
			check(it.isNotBlank()) { "Value must not be blank" }
			System.setProperty(key, it)
		}
	}

	/**
	 * Checks if the proxy server is set.
	 *
	 * @return True if the proxy host and port are set, false otherwise.
	 */
	fun isProxyServerSet() = !proxyHost.isNullOrEmpty() && proxyPort > 0

	/**
	 * Checks if the proxy authentication is set.
	 *
	 * @return True if the proxy user and password are set, false otherwise.
	 */
	fun isProxyAuthSet() = !proxyUser.isNullOrEmpty() && !proxyPassword.isNullOrEmpty()

	/**
	 * Clears all proxy system properties.
	 */
	fun clear() {
		System.clearProperty(key(PROXY_HOST_SYSTEM_PROPERTY))
		System.clearProperty(key(PROXY_PORT_SYSTEM_PROPERTY))
		System.clearProperty(key(PROXY_USER_SYSTEM_PROPERTY))
		System.clearProperty(key(PROXY_PASSWORD_SYSTEM_PROPERTY))
		System.clearProperty(nonProxyHostsKey)
	}

	/**
	 * Exception thrown when the port format is incorrect.
	 *
	 * @param message The exception message.
	 * @param cause The cause of the exception.
	 */
	class IncorrectPortFormatException(message: String, cause: Throwable) : IllegalArgumentException(message, cause)
}