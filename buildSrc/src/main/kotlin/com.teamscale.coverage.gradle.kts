plugins {
	java
	jacoco
}

jacoco {
	toolVersion = versionCatalogs.named("libs").findVersion("jacoco").get().requiredVersion
}

tasks.jacocoTestReport {
	reports {
		xml.required = true
	}
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}
