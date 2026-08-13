import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.Serializable

/** The prefix that the shadow plugin's auto relocation puts all relocated dependency packages under. */
const val SHADOW_PACKAGE_PREFIX = "shadow"

/** Ant patterns under which our own logback configuration files are packaged. */
private val LOGBACK_CONFIG_PATTERNS = listOf("com/teamscale/**/logback*.xml", "logging/logback*.xml")

/** Packages that our logback configuration files reference by fully qualified name and that get relocated. */
private val RELOCATED_LOGGING_PACKAGES = listOf("ch.qos.logback")

/**
 * Whether this build relocates dependencies under [SHADOW_PACKAGE_PREFIX]. Auto relocation is disabled via
 * `-Pdebug=true` to make the agent easier to debug locally.
 */
val Project.usesShadowedPackages: Provider<Boolean>
	get() = providers.gradleProperty("debug").map { it != "true" }.orElse(true)

/**
 * Prefixes references to relocated packages in the logback configuration files packaged by this task, cf.
 * [ShadowLoggingPackages].
 *
 * Whether we relocate is decided by a gradle property, which Gradle does not track when it is read at
 * configuration time, so it is registered as an explicit task input. Otherwise switching between debug and
 * production builds would leave the previously packaged configuration files in place.
 */
fun <T> T.shadowLoggingPackages(relocated: Provider<Boolean>) where T : Task, T : CopySpec {
	inputs.property("shadowedLoggingPackages", relocated)
	filesMatching(LOGBACK_CONFIG_PATTERNS, ShadowLoggingPackages(relocated.get()))
}

/**
 * Registers a [VerifyShadowedLoggingConfigs] task for the given archives and hooks it into `check`.
 *
 * The archives are anything a file collection accepts, in particular the archive tasks that packaged the
 * logback configuration files, e.g. `verifyShadowedLoggingConfigs(tasks.jar)`.
 */
fun Project.verifyShadowedLoggingConfigs(vararg archives: Any) {
	val verifyTask = tasks.register<VerifyShadowedLoggingConfigs>("verifyShadowedLoggingConfigs") {
		this.archives.from(*archives)
		// The Kotlin DSL's assignment operator is not available outside of build scripts.
		relocated.set(usesShadowedPackages)
	}
	tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
		dependsOn(verifyTask)
	}
}

/**
 * Prefixes references to [RELOCATED_LOGGING_PACKAGES] in logback configuration files with
 * [SHADOW_PACKAGE_PREFIX] so they match the relocated classes in the shaded agent jar.
 *
 * This lets us keep the configuration files in the source tree free of the prefix, so they can be used as-is
 * from the IDE, from unit tests and in `-Pdebug=true` builds, where no relocation happens.
 */
private class ShadowLoggingPackages(private val enabled: Boolean) : Action<FileCopyDetails>, Serializable {
	override fun execute(details: FileCopyDetails) {
		if (!enabled) return
		// Anchoring on the opening quote restricts the replacement to XML attribute values, which covers
		// both `class="..."` and `<logger name="...">`.
		details.filter { line ->
			RELOCATED_LOGGING_PACKAGES.fold(line) { result, packageName ->
				result.replace("\"$packageName.", "\"$SHADOW_PACKAGE_PREFIX.$packageName.")
			}
		}
	}
}
