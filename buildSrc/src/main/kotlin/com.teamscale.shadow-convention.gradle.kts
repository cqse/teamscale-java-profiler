import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer

plugins {
	java
	// https://github.com/GradleUp/shadow
	id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
	enableAutoRelocation = providers.gradleProperty("debug").map { it != "true" }.orElse(true)
	archiveClassifier = null as String?
	mergeServiceFiles()
	// Rewrites the package parts inside the .kotlin_module files so they match the relocated
	// classes. Shadow still does this implicitly via the deprecated enableKotlinModuleRemapping
	// flag, but that flag is removed in Shadow 10, so we apply the transformer explicitly.
	transform(KotlinModuleMetadataTransformer::class.java)
	val archiveFile = this.archiveFile
	doLast("revertKotlinPackageChanges") { revertKotlinPackageChanges(archiveFile) }
}
