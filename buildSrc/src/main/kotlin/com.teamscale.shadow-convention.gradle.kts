import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	java
	// https://github.com/GradleUp/shadow
	id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
	enableAutoRelocation = project.properties["debug"] !== "true"
	archiveClassifier = null as String?
	mergeServiceFiles()
	// Relocates the .kotlin_metadata files to ensure reflection in Kotlin does not break
	relocate("kotlin", "shadow.kotlin")
	relocate("okhttp3", "shadow.okhttp3")
	relocate("okio", "shadow.okio")
	relocate("retrofit", "shadow.retrofit")
	val archiveFile = this.archiveFile
	doLast("revertKotlinPackageChanges") { revertKotlinPackageChanges(archiveFile) }
}
