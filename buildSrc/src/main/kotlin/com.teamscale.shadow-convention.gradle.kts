import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer

plugins {
	java
	// https://github.com/GradleUp/shadow
	id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
	enableAutoRelocation = usesShadowedPackages
	archiveClassifier = null as String?
	mergeServiceFiles()
	// The duplicates strategy takes precedence over the transformers and defaults to EXCLUDE, i.e. all
	// but the first copy of a resource are dropped before a transformer gets to see them. Let the
	// transformers handle the resources they merge, keeping EXCLUDE for everything else.
	filesMatching(listOf("META-INF/services/**", "**/*.kotlin_module")) {
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
	// Guards the INCLUDE above: every duplicate we let through must be consumed by a transformer
	// instead of ending up as a duplicate entry in the jar.
	failOnDuplicateEntries = true
	// Our logback configurations are checked in without the shadow prefix so they also work when
	// relocation is disabled. Add the prefix while packaging them into the shaded jar.
	shadowLoggingPackages(usesShadowedPackages)
	// Rewrites the package parts inside the .kotlin_module files so they match the relocated
	// classes. Shadow still does this implicitly via the deprecated enableKotlinModuleRemapping
	// flag, but that flag is removed in Shadow 10, so we apply the transformer explicitly.
	transform(KotlinModuleMetadataTransformer::class.java)
	val archiveFile = this.archiveFile
	doLast("revertKotlinPackageChanges") { revertKotlinPackageChanges(archiveFile) }
}
