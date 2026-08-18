plugins {
	java
	`maven-publish`
	signing
}

val extension = extensions.create<PublicationInfoExtension>("publishAs", project)

java {
	withJavadocJar()
	withSourcesJar()
}

tasks.named<Javadoc>("javadoc") {
	(options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
	publications {
		configureEach {
			extension.applyTo(this)
		}
		// We don't want to create the publication for the Gradle plugin project as it creates its own publication
		if (project.name != "teamscale-gradle-plugin") {
			configureMavenPublication()
		}
	}
}

// Contributes this project's publications to the aggregated deployment that the root project uploads to
// Maven Central. The Gradle plugin is released through the Gradle Plugin Portal instead, so it stays out.
if (project.name != "teamscale-gradle-plugin") {
	pluginManager.apply("com.gradleup.nmcp")
}

signing {
	setRequired({
		// Do not require signing for deployment to maven local
		project.findProperty("signing.password") != null && project.findProperty("signing.secretKeyRingFile") != null
	})
	sign(publishing.publications)
}

fun PublicationContainer.configureMavenPublication() {
	create<MavenPublication>("maven") {
		val publication = this
		var hasShadow = false
		pluginManager.withPlugin("com.teamscale.shadow-convention") {
			publication.from(components.findByName("shadow"))
			setArtifacts(listOf(tasks["shadowJar"]))
			artifact(tasks["sourcesJar"])
			artifact(tasks["javadocJar"])
			hasShadow = true
		}

		versionMapping {
			usage(Usage.JAVA_RUNTIME) {
				fromResolutionOf("runtimeClasspath")
			}
		}

		// we do not want to publish both the shadow and the normal jar (this causes errors during publishing)
		if (!hasShadow) {
			pluginManager.withPlugin("java-library") {
				from(components["java"])
			}
			pluginManager.withPlugin("org.gradlex.maven-plugin-development") {
				from(components["java"])
			}
		}
	}
}

tasks.withType<GenerateModuleMetadata> {
	dependsOn(tasks.named("jar"))
}
