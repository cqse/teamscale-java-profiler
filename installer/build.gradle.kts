import org.beryx.jlink.BaseTask
import org.beryx.jlink.JlinkTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	application
	com.teamscale.`java-convention`
	com.teamscale.coverage
	com.teamscale.`system-test-convention`
	alias(libs.plugins.jlink)
}

tasks.jar {
	manifest {
		attributes(
			"Main-Class" to "com.teamscale.profiler.installer.RootCommand",
		)
	}
}

// Workaround for https://youtrack.jetbrains.com/issue/KT-55389
tasks.compileJava {
	// Typed as FileCollection because the configuration cache cannot restore the captured value
	// into a field declared as the more specific SourceSetOutput.
	val mainSourceSetOutput: FileCollection = sourceSets.main.get().output
	options.compilerArgumentProviders.add(CommandLineArgumentProvider {
		listOf(
			"--patch-module",
			"com.teamscale.profiler.installer=${mainSourceSetOutput.asPath}"
		)
	})
}

tasks.withType<JavaCompile> {
	options.release = 21
}

tasks.withType<KotlinCompile> {
	compilerOptions.jvmTarget = JvmTarget.JVM_21
}

// Shares the jlink runtime image with the :agent project, which ships it in its distribution.
configurations.consumable(JLINK_IMAGE_CONFIGURATION)
artifacts.add(JLINK_IMAGE_CONFIGURATION, tasks.named<JlinkTask>("jlink").map { it.imageDir }) {
	type = "directory"
	builtBy(tasks.named("jlink"))
}

application {
	applicationName = "installer"
	mainClass = "com.teamscale.profiler.installer.RootCommand"
	mainModule = "com.teamscale.profiler.installer"
	applicationDefaultJvmArgs = listOf(
		// Ensure that no stack traces are lost.
		// See <https://stackoverflow.com/questions/2411487/nullpointerexception-in-java-with-no-stacktrace>
		"-XX:-OmitStackTraceInFastThrow",
	)
}

val runtimeJdkVersion = providers.gradleProperty("runtimeJdkVersion").get()

/**
 * Provisions the JDK that the runtime image for the given operating system is linked against and returns the
 * path to its JDK home.
 *
 * The jlink plugin can download the JDK itself via `jdkDownload`, but it stores that download as a lazily
 * evaluated Groovy closure in its `targetPlatforms` input. The configuration cache replaces the closure's
 * owner with a non-serializable BrokenObject, which makes fingerprinting that input fail once the task graph
 * is restored. Handing jlink a plain path keeps the input serializable, and declaring the archive as a
 * dependency lets Gradle cache it across builds instead of re-downloading it into the build directory.
 *
 * The archives are resolved from the Adoptium repository declared in settings.gradle.kts, which is what the
 * `net.adoptium.cdn` group below refers to.
 */
fun provisionRuntimeJdk(operatingSystem: String, archiveExtension: String): String {
	val archiveName = "OpenJDK${runtimeJdkVersion.substringBefore(".")}U-jdk_x64_" +
			"${operatingSystem}_hotspot_${runtimeJdkVersion.replace("+", "_")}"

	val jdk = configurations.dependencyScope("${operatingSystem}RuntimeJdk")
	val jdkArchive = configurations.resolvable("${operatingSystem}RuntimeJdkArchive") {
		extendsFrom(jdk.get())
	}
	dependencies.add(jdk.name, "net.adoptium.cdn:$archiveName@$archiveExtension")

	val jdkHome = layout.buildDirectory.dir("runtime-jdks/$operatingSystem")
	val unpackJdk = tasks.register<Sync>("unpack${operatingSystem.replaceFirstChar(Char::titlecase)}RuntimeJdk") {
		description = "Unpacks the JDK that the $operatingSystem runtime image is linked against."
		// The archive tree is built during configuration, so that the copy action does not have to reach back
		// into the build script at execution time.
		val archiveFile = jdkArchive.map { it.singleFile }
		from(if (archiveExtension == "zip") zipTree(archiveFile) else tarTree(archiveFile)) {
			// Everything sits below a single jdk-<version> folder, which we strip to get a predictable path.
			eachFile {
				relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
			}
			includeEmptyDirs = false
		}
		into(jdkHome)
	}

	// The JDK home is a plain string, so Gradle cannot infer this dependency by itself. CreateMergedModuleTask
	// and JlinkTask both extend BaseTask, so this covers every task that reads a target platform.
	tasks.withType<BaseTask> {
		dependsOn(unpackJdk)
	}

	return jdkHome.get().asFile.absolutePath
}

jlink {
	forceMerge("kotlin")
	options = listOf(
		"--no-header-files",
		"--no-man-pages",
		"--dedup-legal-notices", "error-if-not-same-content"
	)
	launcher {
		name = "installer"
	}

	targetPlatform("linux-x86_64") {
		setJdkHome(provisionRuntimeJdk("linux", "tar.gz"))
	}
	targetPlatform("windows-x86_64") {
		setJdkHome(provisionRuntimeJdk("windows", "zip"))
	}
}

dependencies {
	implementation(libs.okhttp.core)
	implementation(libs.commonsLang)
	implementation(libs.commonsIo)
	implementation(libs.picocli.core)
	annotationProcessor(libs.picocli.codegen)
	implementation(libs.jna.platform)

	testImplementation(libs.spark)
	testImplementation(project(":common-system-test"))
}
