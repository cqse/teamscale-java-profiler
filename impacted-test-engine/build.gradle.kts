plugins {
	`java-library`
	com.teamscale.`kotlin-convention`
	com.teamscale.coverage
	com.teamscale.`shadow-convention`
	com.teamscale.publish
}

publishAs {
	readableName = "Impacted Test Engine"
	description = "A JUnit 5 engine that handles retrieving impacted tests from Teamscale and organizes their execution"
}

dependencies {
	// Fixed to 5.x because 6.x requires Java 17
	implementation(platform("org.junit:junit-bom:5.14.3"))
	implementation(project(":teamscale-client"))
	implementation(project(":report-generator"))
	implementation(project(":tia-client"))

	compileOnly(libs.junit.platform.engine)
	compileOnly(libs.junit.platform.commons)
	testImplementation(libs.junit.platform.engine)
	testImplementation(libs.junit.jupiter.params)
	testImplementation(libs.mockito.kotlin)
}
