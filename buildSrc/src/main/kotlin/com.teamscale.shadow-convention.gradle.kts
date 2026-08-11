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
