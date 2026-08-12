plugins {
    com.teamscale.`kotlin-convention`
    com.teamscale.`system-test-convention`
    com.teamscale.coverage
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "systemundertest.SystemUnderTest"
    }
    // create a fat jar so the Kotlin standard library is available when the jar is run via `java -jar`
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    environment("AGENT_JAR", agentJar)
    environment("SYSTEM_UNDER_TEST_JAR", tasks.jar.get().outputs.files.singleFile)
    dependsOn(tasks.jar)
}
