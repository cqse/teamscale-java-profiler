plugins {
	id("com.teamscale.java-convention")
	id("com.teamscale.agent-jar")
}

val provider = SystemTestPorts.registerWith(project)

/**
 * Port on which JVMs spawned by the system tests wait for a debugger, requested via `-PdebugSut[=<port>]`.
 * Absent unless the property is set, in which case no JVM waits for anything.
 */
val debugSutPort = providers.gradleProperty("debugSut")
	.map { if (it.isEmpty() || it == "true") "5005" else it }

/**
 * The oldest Java version that the profiler supports. Our build and tests run on newer versions, so the system tests
 * that start the profiled application themselves run it on this one, cf. SystemTestUtils.OLDEST_SUPPORTED_JAVA.
 */
val oldestSupportedJava = javaToolchains.launcherFor {
	languageVersion = JavaLanguageVersion.of(11)
}

tasks.test {
	usesService(provider)
	// The name of the property is repeated in SystemTestUtils, which reads it.
	jvmArgumentProviders.add(JavaExecutableArgumentProvider("oldestSupportedJava", oldestSupportedJava))

	// The spawned JVM suspends until a debugger attaches, so the test must not be run in parallel with others
	// and must not inherit a timeout. Both are the caller's responsibility (see docs/DEBUGGING.md).
	debugSutPort.orNull?.let { environment("SYSTEM_TEST_DEBUG_PORT", it) }

	val teamscalePort = provider.get().pickFreePort()
	val agentPort = provider.get().pickFreePort()
	extensions.create<PortsExtension>("ports", provider).apply {
		this.teamscalePort = teamscalePort
		this.agentPort = agentPort
	}

	systemProperties("agentPort" to agentPort, "teamscalePort" to teamscalePort)
	environment("AGENT_VERSION", version)
	environment("AGENT_PATH", agentJar)
	environment("TEAMSCALE_PORT", teamscalePort)
	environment("AGENT_PORT", agentPort)

	val dir = layout.projectDirectory.dir(logFilePath)
	doFirst {
		dir.asFile.deleteRecursively()
	}
}

dependencies {
	testImplementation(project(":common-system-test"))
}
