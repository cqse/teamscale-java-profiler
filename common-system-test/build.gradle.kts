plugins {
	`java-library`
	com.teamscale.`kotlin-convention`
	// we do not enable code coverage recording for the system tests as we already need our agent attached
	// it would conflict with JaCoCo's
}

dependencies {
	api(project(":teamscale-client"))
	api(project(":tia-client"))

	api(libs.spark)
	api(libs.jackson.databind)
	runtimeOnly(libs.log4j.core)
}
