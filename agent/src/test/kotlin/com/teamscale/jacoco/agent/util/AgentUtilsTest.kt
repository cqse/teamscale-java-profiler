package com.teamscale.jacoco.agent.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Paths

/** Tests the [AgentUtils].  */
class AgentUtilsTest {
	@Test
	fun findsAgentJarAtItsCodeSourceLocation() {
		val agentJar = AgentUtils.agentJarFileFromCodeSource(
			URI.create("file:/opt/profiler/lib/teamscale-jacoco-agent.jar").toURL()
		)

		assertThat(agentJar).isEqualTo(Paths.get("/opt/profiler/lib/teamscale-jacoco-agent.jar"))
	}

	@Test
	fun findsNoAgentJarWithoutCodeSourceLocation() {
		val agentJar = AgentUtils.agentJarFileFromCodeSource(null)

		assertThat(agentJar).isNull()
	}

	@Test
	fun findsNoAgentJarAtNonFileCodeSourceLocation() {
		val agentJar = AgentUtils.agentJarFileFromCodeSource(
			URI.create("jar:file:/opt/app.jar!/lib/teamscale-jacoco-agent.jar").toURL()
		)

		assertThat(agentJar).isNull()
	}

	@Test
	fun findsAgentJarAmongOtherJavaAgents() {
		val agentJar = AgentUtils.agentJarFileFromJavaAgentJvmArgument(
			listOf(
				"-Dfoo=bar",
				"-javaagent:/tmp/mockito-core-5.23.0.jar",
				"-javaagent:/opt/profiler/lib/teamscale-jacoco-agent-38.1.0.jar=mode=testwise,out=/tmp/out",
				"-Xmx1g"
			)
		)

		assertThat(agentJar).isEqualTo(Paths.get("/opt/profiler/lib/teamscale-jacoco-agent-38.1.0.jar"))
	}

	@Test
	fun findsAgentJarWithoutVersionSuffix() {
		val agentJar = AgentUtils.agentJarFileFromJavaAgentJvmArgument(
			listOf("-javaagent:/opt/profiler/lib/teamscale-jacoco-agent.jar=mode=testwise,out=/tmp/out")
		)

		assertThat(agentJar).isEqualTo(Paths.get("/opt/profiler/lib/teamscale-jacoco-agent.jar"))
	}

	@Test
	fun findsAgentJarWithoutAgentOptions() {
		val agentJar = AgentUtils.agentJarFileFromJavaAgentJvmArgument(
			listOf("-javaagent:/opt/profiler/lib/teamscale-jacoco-agent-38.1.0.jar")
		)

		assertThat(agentJar).isEqualTo(Paths.get("/opt/profiler/lib/teamscale-jacoco-agent-38.1.0.jar"))
	}

	@Test
	fun findsNoAgentJarWhenNoJavaAgentNamesIt() {
		val agentJar = AgentUtils.agentJarFileFromJavaAgentJvmArgument(
			listOf("-javaagent:/tmp/mockito-core-5.23.0.jar")
		)

		assertThat(agentJar).isNull()
	}
}
