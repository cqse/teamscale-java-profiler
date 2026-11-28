import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Asserts that none of the jars on the given classpath contains classes for a newer Java version than
 * [javaVersion].
 *
 * `--release` only restricts our own classes, so without this check a dependency update could silently raise the
 * Java version the profiler requires. Classes below `META-INF/versions` are ignored, since multi-release jars ship
 * them for newer Java versions on purpose and older JVMs do not load them.
 */
abstract class VerifyBytecodeVersion : DefaultTask() {

	/** The jars to check. Nested jars are not inspected. */
	@get:Classpath
	abstract val classpath: ConfigurableFileCollection

	/** The newest Java version the classes may target. */
	@get:Input
	abstract val javaVersion: Property<Int>

	/** Checks the class files of every jar, cf. [VerifyBytecodeVersion]. */
	@TaskAction
	fun verify() {
		val maxJavaVersion = javaVersion.get()
		val violations = classpath.mapNotNull { jar ->
			val newestClass = findNewestClass(jar)
			if (newestClass == null || newestClass.javaVersion <= maxJavaVersion) return@mapNotNull null
			"${jar.name} contains classes for Java ${newestClass.javaVersion}, e.g. ${newestClass.path}"
		}
		if (violations.isNotEmpty()) {
			throw GradleException(
				"The following dependencies require a newer Java version than $maxJavaVersion:\n" +
						violations.joinToString("\n") { "- $it" }
			)
		}
	}

	private fun findNewestClass(jar: File) = ZipFile(jar).use { zip ->
		zip.entries().asSequence()
			.filter { it.name.endsWith(".class") && !it.name.startsWith(MULTI_RELEASE_DIRECTORY) }
			.map { ClassFile(it.name, readJavaVersion(zip.getInputStream(it))) }
			.maxByOrNull { it.javaVersion }
	}

	private fun readJavaVersion(classFile: InputStream) = DataInputStream(classFile).use {
		it.readInt() // magic
		it.readUnsignedShort() // minor version
		it.readUnsignedShort() - MAJOR_VERSION_OFFSET
	}

	/** A class file in a jar and the Java version it was compiled for. */
	private data class ClassFile(
		/** The path of the class file within the jar. */
		val path: String,
		/** The Java version that the class file's major version stands for. */
		val javaVersion: Int
	)

	private companion object {
		const val MULTI_RELEASE_DIRECTORY = "META-INF/versions/"

		/** The difference between a class file major version and its Java version, e.g. 55 for Java 11. */
		const val MAJOR_VERSION_OFFSET = 44
	}
}
