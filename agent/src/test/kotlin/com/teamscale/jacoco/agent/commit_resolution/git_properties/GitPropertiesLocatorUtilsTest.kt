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

	@Test
	@Throws(Exception::class)
	fun parseVfsCodeLocationsWithLiteralSpaces() {
		val url = URI("vfs", null, "/content/my app.war/WEB-INF/classes", null).toURL()
		val method = GitPropertiesLocatorUtils::class.java.getDeclaredMethod("getVfsContentFolder", URL::class.java)
		method.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		val result = method.invoke(GitPropertiesLocatorUtils, url) as Pair<File, Boolean>
		Assertions.assertThat(result.first).isEqualTo(File("/tmp/vfs-content/my app.war"))
		Assertions.assertThat(result.second).isTrue()
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
				when (protocol) {
					"nested" -> object : URLStreamHandler() {
						/** Returns null, since opening the connection is never done in the test.:  */
						override fun openConnection(url: URL?) = null
					}
					"vfs" -> object : URLStreamHandler() {
						override fun openConnection(url: URL?): URLConnection = object : URLConnection(url) {
							override fun connect() {
							}

							override fun getContent(): Any = FakeVfsVirtualFile(File("/tmp/vfs-content/my app.war"))
						}
					}
					else -> null
				}
			}
		}
	}
}

class FakeVfsVirtualFile(private val physicalFile: File) {
	@Suppress("unused")
	fun getPhysicalFile(): File = physicalFile
}