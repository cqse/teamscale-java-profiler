plugins {
	java
	jacoco
}

jacoco {
	toolVersion = "0.8.16"
}

tasks.jacocoTestReport {
	reports {
		xml.required = true
	}
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}
