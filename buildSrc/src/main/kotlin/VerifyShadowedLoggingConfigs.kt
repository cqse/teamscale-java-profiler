import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Asserts that the logback configuration files packaged into the given archives reference the relocated
 * logback classes, i.e. that [ShadowLoggingPackages] was applied to all of them.
 *
 * The archives are scanned for logback configurations instead of checking a fixed list of files, so this also
 * fails if a newly added configuration file is not covered by [LOGBACK_CONFIG_PATTERNS].
 */
abstract class VerifyShadowedLoggingConfigs : DefaultTask() {

	/** The archives to check. Nested archives are not inspected. */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val archives: ConfigurableFileCollection

	/** Whether the build relocates dependencies, cf. [usesShadowedPackages]. */
	@get:Input
	abstract val relocated: Property<Boolean>

	/** Checks every logback configuration in every archive, cf. [VerifyShadowedLoggingConfigs]. */
	@TaskAction
	fun verify() {
		val isRelocated = relocated.get()
		archives.forEach { archive ->
			ZipFile(archive).use { zip ->
				val configs = zip.entries().asSequence()
					.filter { CONFIG_NAME.matches(it.name.substringAfterLast('/')) }
					.associate { it.name to zip.getInputStream(it).reader().readText() }
				if (configs.isEmpty()) {
					throw GradleException("Did not find any logback configuration in ${archive.name}")
				}
				configs.forEach { (path, content) -> verify(archive, path, content, isRelocated) }
			}
		}
	}

	private fun verify(archive: File, path: String, content: String, relocated: Boolean) {
		// Every configuration references logback classes, so this one doubles as the canary for a
		// configuration file that the packaging did not rewrite at all.
		val relocatedReference = "\"$SHADOW_PACKAGE_PREFIX.$LOGBACK_PACKAGE."
		if (relocated && !content.contains(relocatedReference)) {
			throw GradleException(
				"$path in ${archive.name} does not reference any relocated logback class." +
						" Is it covered by one of the LOGBACK_CONFIG_PATTERNS?"
			)
		}
		if (!relocated && content.contains(relocatedReference)) {
			throw GradleException(
				"$path in ${archive.name} references relocated logback classes," +
						" but this build does not relocate anything."
			)
		}

		// The agent's own classes are relocated as well, so a configuration naming one of them by its plain
		// name would fail at logging initialisation with a ClassNotFoundException.
		RELOCATED_LOGGING_PACKAGES.forEach { packageName ->
			if (relocated && content.contains("\"$packageName.")) {
				throw GradleException(
					"$path in ${archive.name} still references non-relocated $packageName classes," +
							" which do not exist in the shaded jar."
				)
			}
		}
	}

	private companion object {
		val CONFIG_NAME = Regex("logback.*\\.xml")
		const val LOGBACK_PACKAGE = "ch.qos.logback"
	}
}
