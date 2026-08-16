import com.github.iherasymenko.jlink.JlinkImageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	application
	com.teamscale.`java-convention`
	com.teamscale.coverage
	alias(libs.plugins.jlink)
	alias(libs.plugins.extraJavaModuleInfo)
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

// jlink can only link real modules, so every dependency that ships without a module descriptor gets one
// here. Only the main source set needs them; the tests run on the classpath, and patching their
// dependencies (spark and its Jetty stack in particular) would be pure busywork.
// `failOnAutomaticModules` is what keeps this list honest: a new non-modular dependency fails the build
// instead of silently ending up as an automatic module that jlink then refuses to link.
extraJavaModuleInfo {
	failOnAutomaticModules = true
	deactivate(sourceSets.test)
	deactivate(configurations.annotationProcessor)

	module("com.squareup.okio:okio-jvm", "okio") {
		requires("kotlin.stdlib")
		exportAllPackages()
	}
	module("net.java.dev.jna:jna", "com.sun.jna") {
		exportAllPackages()
	}
	module("net.java.dev.jna:jna-platform", "com.sun.jna.platform") {
		requires("com.sun.jna")
		exportAllPackages()
	}
	// Annotations with class file retention that kotlin-stdlib pulls in. Nothing reads them at runtime, but
	// they are on the module path and thus need a descriptor like everything else.
	module("org.jetbrains:annotations", "org.jetbrains.annotations") {
		exportAllPackages()
	}
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

// The launcher name, main module and main class come from the application block above, and so do the JVM
// arguments, which jlink bakes into the image as `--add-options`.
jlinkApplication {
	noHeaderFiles = true
	noManPages = true
	dedupLegalNoticesErrorIfNotSameContent = true
}

/**
 * The coordinates of the Adoptium archive holding the JDK that the runtime image for the given operating
 * system is linked against, resolved from the repository declared in settings.gradle.kts.
 */
fun jdkArchive(operatingSystem: String, archiveExtension: String): String {
	val runtimeJdkVersion = providers.gradleProperty("runtimeJdkVersion").get()
	return "net.adoptium.cdn:OpenJDK${runtimeJdkVersion.substringBefore(".")}U-jdk_x64_" +
			"${operatingSystem}_hotspot_${runtimeJdkVersion.replace("+", "_")}@$archiveExtension"
}

// jlink links against the `jmods` folder of the JDK it is given rather than the one it runs on, so a single
// machine builds the images for every operating system. Each entry adds an `image<Name>` task, which the
// plugin wires into `assemble`, and a `jdkArchive<Name>` configuration holding the JDK to link against.
//
// That JDK is declared below rather than through the plugin's own `group`/`jdkArchive` properties, because
// the plugin turns those into a dependency in the map notation that Gradle 9 deprecated and Gradle 10
// removes. Leaving them unset keeps the dependency it adds empty, so ours is the only one.
jlinkImages {
	create("linux")
	create("windows")
}

dependencies {
	"jdkArchiveLinux"(jdkArchive("linux", "tar.gz"))
	"jdkArchiveWindows"(jdkArchive("windows", "zip"))
}

/**
 * The directory holding the runtime images of all operating systems, which :agent consumes as a whole.
 *
 * The names of the subdirectories below it are part of the distribution's layout and must not change:
 * `agent/src/dist/installer.sh` and `installer.bat` start the launcher inside them, and `Installer` derives
 * the directory to install from by walking up from its own `java.home`.
 */
val imagesDirectory = layout.buildDirectory.dir("installer-images")

mapOf("Linux" to "linux", "Windows" to "windows").forEach { (imageName, operatingSystem) ->
	tasks.named<JlinkImageTask>("image$imageName") {
		output = imagesDirectory.map { it.dir("installer-$operatingSystem-x86_64") }
	}
}

// Shares the jlink runtime images with the :agent project, which ships them in its distribution.
configurations.consumable(JLINK_IMAGE_CONFIGURATION)
artifacts.add(JLINK_IMAGE_CONFIGURATION, imagesDirectory) {
	type = "directory"
	builtBy(tasks.named("imageLinux"), tasks.named("imageWindows"))
}

// The tests start a mock Teamscale server and thus need a port that no other test uses. They take it from
// the same shared service as the system tests, but do not apply com.teamscale.system-test-convention: they
// are plain unit tests and need none of the rest of it, in particular not the agent jar.
val portProvider = SystemTestPorts.registerWith(project)

tasks.test {
	usesService(portProvider)
	systemProperty("teamscalePort", portProvider.get().pickFreePort())
}

dependencies {
	implementation(libs.okhttp.core)
	implementation(libs.commonsLang)
	implementation(libs.commonsIo)
	implementation(libs.picocli.core)
	annotationProcessor(libs.picocli.codegen)
	implementation(libs.jna.platform)

	testImplementation(libs.spark)
}
