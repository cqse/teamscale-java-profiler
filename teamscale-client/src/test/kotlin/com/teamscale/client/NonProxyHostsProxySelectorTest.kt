package com.teamscale.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

internal class NonProxyHostsProxySelectorTest {

	@ParameterizedTest(name = "nonProxyHosts={0}, host={1} -> direct={2}")
	@CsvSource(
		// The JVM's defaults apply if nothing is set
		",localhost,true",
		",127.0.0.1,true",
		",[::1],true",
		",0.0.0.0,true",
		",teamscale.example.com,false",
		// Exact host
		"teamscale.mycompany.com,teamscale.mycompany.com,true",
		"teamscale.mycompany.com,other.mycompany.com,false",
		"teamscale.mycompany.com,teamscaleXmycompany.com,false",
		// Wildcards at the start and/or end
		"*.mycompany.com,teamscale.mycompany.com,true",
		"*.mycompany.com,mycompany.com,false",
		"teamscale.*,teamscale.mycompany.com,true",
		"teamscale.*,my-teamscale.com,false",
		"*internal*,teamscale.internal.mycompany.com,true",
		"*,teamscale.example.com,true",
		// Several patterns
		"other.com|*.mycompany.com,teamscale.mycompany.com,true",
		"other.com|*.mycompany.com,teamscale.example.com,false",
		// Case-insensitive
		"*.MyCompany.com,Teamscale.mycompany.COM,true",
		// The JVM's defaults are added to a non-empty value
		"*.mycompany.com,localhost,true",
		// An empty value excludes no host
		"'',localhost,false",
	)
	fun testSelect(nonProxyHosts: String?, host: String, expectDirect: Boolean) {
		val selector = NonProxyHostsProxySelector(PROXY, nonProxyHosts)

		val expectedProxy = if (expectDirect) Proxy.NO_PROXY else PROXY
		assertThat(selector.select(URI("https://$host/api"))).containsExactly(expectedProxy)
	}

	companion object {
		private val PROXY = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.mycompany.com", 3128))
	}
}
