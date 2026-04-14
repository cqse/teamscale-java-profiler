package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.extractGitPropertiesSearchRoot
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

internal class GitPropertiesLocatorUtilsTest {
	@Test
	@Throws(Exception::class)
	fun parseSpringBootCodeLocations() {
		val url = URI.create("jar:file:/home/k/demo.jar!/BOOT-INF/classes!/").toURL()
		Assertions.assertThat(
			extractGitPropertiesSearchRoot(url)!!.first
		).isEqualTo(File("/home/k/demo.jar"))

		val springBoot3Url = URI.create(
			"jar:nested:/home/k/proj/spring-boot/demo/build/libs/demo-0.0.1-SNAPSHOT.jar/!BOOT-INF/classes/!/."
		).toURL()
		Assertions.assertThat(extractGitPropertiesSearchRoot(springBoot3Url)!!.first)
			.isEqualTo(File("/home/k/proj/spring-boot/demo/build/libs/demo-0.0.1-SNAPSHOT.jar"))
	}

	@Test
	@Throws(Exception::class)
	fun parseFileCodeLocations() {
		val url = URI.create("file:/home/k/demo.jar").toURL()
		Assertions.assertThat(
			extractGitPropertiesSearchRoot(url)!!.first
		).isEqualTo(File("/home/k/demo.jar"))
	}

	companion object {
		/**
		 * Registers a protocol handler so the test can construct "nested:" URLs that are not supported by plain Java
		 * but Spring boot.
		 */
		@JvmStatic
		@BeforeAll
		fun registerCatchAllUrlProtocol() {
			URL.setURLStreamHandlerFactory { protocol ->
				if ("nested" != protocol) {
					return@setURLStreamHandlerFactory null
				}
				object : URLStreamHandler() {
					/** Returns null, since opening the connection is never done in the test.:  */
					override fun openConnection(url: URL?): URLConnection? {
						return null
					}
				}
			}
		}
	}
}