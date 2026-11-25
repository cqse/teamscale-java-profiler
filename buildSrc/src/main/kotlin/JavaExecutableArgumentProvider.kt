import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes the `java` executable of a toolchain to the test JVM as a system property. The launcher is a nested input,
 * so the tests rerun when they would get a different JDK.
 */
class JavaExecutableArgumentProvider(
	/** The name of the system property. */
	@get:Input val propertyName: String,
	/** The toolchain whose `java` executable is passed. */
	@get:Nested val launcher: Provider<JavaLauncher>
) : CommandLineArgumentProvider {
	override fun asArguments() = listOf("-D$propertyName=${launcher.get().executablePath.asFile.absolutePath}")
}
