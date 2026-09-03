import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.Serializable

// Records the coverage of our own production classes inside the JVMs that this project's tests spawn.
//
// Those tests do their real work in the Maven and Gradle builds they start as child processes, so the JaCoCo
// agent that com.teamscale.coverage attaches to the test JVM never sees the classes that matter: the Mojos of
// the Maven plugin, the tasks of the Gradle plugin and the report generator they call into. This plugin
// attaches a second, plain JaCoCo agent to those child processes and folds what it records into the project's
// jacocoTestReport, which is the report that CI uploads.

plugins {
	id("com.teamscale.coverage")
}

// The version catalog accessors are not available in precompiled script plugins, cf. com.teamscale.java-convention.
val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("libs")

/**
 * The plain JaCoCo agent that instruments our classes in the spawned JVMs. Not the profiler, cf. the note above.
 * Deliberately not named jacocoAgent: inside dependencies {} that name binds to the accessor of the JaCoCo
 * plugin's own configuration of that name rather than to this variable, which would silently leave this one empty.
 */
val spawnedJvmJacocoAgent = configurations.dependencyScope("spawnedJvmJacocoAgent")
val jacocoAgentJar = configurations.resolvable("spawnedJvmJacocoAgentPath") {
	extendsFrom(spawnedJvmJacocoAgent.get())
}

/**
 * The projects whose production classes run inside the spawned JVMs, declared by the consuming build script.
 * Their class and source directories are resolved through this configuration instead of being read from the
 * projects directly, which is what keeps the build compatible with project isolation.
 */
val spawnedJvmCode = configurations.dependencyScope("spawnedJvmCode")
val spawnedJvmCodePath = configurations.resolvable("spawnedJvmCodePath") {
	extendsFrom(spawnedJvmCode.get())
	// Every project that runs in a spawned JVM is named explicitly, so that the report does not also list the
	// classes that such a project merely depends on. Those would show up as entirely uncovered, because they
	// either do not run at all or, like the profiler, only run under their relocated names.
	isTransitive = false
	// The attributes of a runtime classpath. Asking for Bundling.EXTERNAL is also what selects the plain
	// variant of the projects that publish a shaded jar, whose classes we could not map back to the sources.
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
		attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
		attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named<LibraryElements>(LibraryElements.JAR))
		attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named<Bundling>(Bundling.EXTERNAL))
		attribute(
			TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
			objects.named<TargetJvmEnvironment>(TargetJvmEnvironment.STANDARD_JVM)
		)
	}
}

dependencies {
	spawnedJvmJacocoAgent("org.jacoco:org.jacoco.agent:${libs.findVersion("jacoco").get().requiredVersion}:runtime")
}

/**
 * The file that every JVM spawned by this project's tests appends its coverage to. One file for all of them is
 * safe: JaCoCo takes an exclusive lock on the destination file before writing it and reads the resulting
 * concatenation back as consecutive sessions.
 */
val spawnedJvmExecutionData = layout.buildDirectory.file("jacoco/spawnedJvms.exec")

tasks.test {
	// Resolved eagerly so that the option is a plain string, cf. multiple-agents-test, which attaches a
	// foreign JaCoCo agent to its own test JVM the same way.
	val agentJar = jacocoAgentJar.get().singleFile
	val destination = spawnedJvmExecutionData.get().asFile
	outputs.file(spawnedJvmExecutionData)
	// The include pattern matches the VM names of the classes, so it deliberately does not match the
	// shadow.com.teamscale.* classes of the profiler, which is attached to some of the same JVMs. Both
	// agents therefore instrument a disjoint set of classes and cannot interfere with each other.
	// The name of the property is repeated in ProcessUtils, which reads it.
	systemProperty(
		"systemTestCoverageAgent",
		"-javaagent:$agentJar=destfile=$destination,append=true,output=file,dumponexit=true,jmx=false," +
				"includes=com.teamscale.*"
	)
	doFirst("deleteSpawnedJvmCoverage", DeleteFile(destination))
}

tasks.jacocoTestReport {
	executionData(spawnedJvmExecutionData)
	// A system test has no production code of its own, so without these its report stays empty no matter how
	// much coverage was recorded. Projects that do have production code, like the Gradle plugin, declare
	// nothing here and keep the class directories that the jacoco plugin derives from their own source set.
	classDirectories.from(spawnedJvmCodePath.get().incoming.artifactView {
		componentFilter { it is ProjectComponentIdentifier }
		attributes.attribute(
			LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named<LibraryElements>(LibraryElements.CLASSES)
		)
	}.files)
	// Only needed for the HTML report; Teamscale maps the XML report onto the sources by package and file
	// name. Resolved leniently so that a project without a sources variant cannot fail the build over it.
	sourceDirectories.from(spawnedJvmCodePath.get().incoming.artifactView {
		withVariantReselection()
		lenient(true)
		componentFilter { it is ProjectComponentIdentifier }
		attributes {
			attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.VERIFICATION))
			attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named<Bundling>(Bundling.EXTERNAL))
			attribute(
				VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
				objects.named<VerificationType>(VerificationType.MAIN_SOURCES)
			)
		}
	}.files)
}

/** Deletes the given file before the task runs, so that a run never reports the coverage of the previous one. */
class DeleteFile(private val file: File) : Action<Task>, Serializable {
	override fun execute(t: Task) {
		file.delete()
	}
}
