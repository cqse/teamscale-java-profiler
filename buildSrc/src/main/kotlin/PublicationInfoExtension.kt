import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.Publication
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.property

/** Extension that holds the information that will end up in the POM file published to Maven Central. */
open class PublicationInfoExtension(objectFactory: ObjectFactory, val project: Project) {
	val artifactId: Property<String> = objectFactory.property<String>().convention(project.name)
	val readableName: Property<String> = objectFactory.property<String>()
	val description: Property<String> = objectFactory.property<String>()

	fun applyTo(publication: Publication) {
		if (publication is MavenPublication) {
			if (!publication.name.contains("PluginMarkerMaven")) {
				project.afterEvaluate {
					publication.artifactId = artifactId.get()
				}
			}
			publication.pom.configureGeneralPomInfo(this)
		}
	}
}

fun MavenPom.configureGeneralPomInfo(publicationInfo: PublicationInfoExtension) {
	this.name.set(publicationInfo.readableName)
	this.description.set(publicationInfo.description)
	this.url.set("https://github.com/cqse/teamscale-jacoco-agent/")
	licenses {
		license {
			name.set("The Apache License, Version 2.0")
			url.set("https://spdx.org/licenses/Apache-2.0")
		}
	}
	developers {
		developer {
			id.set("cqse")
			name.set("CQSE GmbH")
			email.set("support@cqse.eu")
		}
	}
	scm {
		connection.set("scm:git:https://github.com/cqse/teamscale-jacoco-agent.git")
		developerConnection.set("scm:git:https://github.com/cqse/teamscale-jacoco-agent.git")
		url.set("https://github.com/cqse/teamscale-jacoco-agent/")
	}
}
