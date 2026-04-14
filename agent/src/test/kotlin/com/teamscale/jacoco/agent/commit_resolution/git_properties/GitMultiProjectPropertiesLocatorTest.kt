package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.options.ProjectAndCommit
import com.teamscale.jacoco.agent.upload.teamscale.DelayedTeamscaleMultiProjectUploader
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.stream.Collectors

internal class GitMultiProjectPropertiesLocatorTest {
	@Test
	fun testNoErrorIsThrownWhenGitPropertiesFileDoesNotHaveAProject() {
		val projectAndCommits = mutableListOf<ProjectAndCommit>()
		val locator = GitMultiProjectPropertiesLocator(
			DelayedTeamscaleMultiProjectUploader { project, revision ->
				projectAndCommits.add(ProjectAndCommit(project, revision))
				TeamscaleServer()
			}, true, null
		)
		val jarFile = File(javaClass.getResource("emptyTeamscaleProjectGitProperties")!!.file)
		locator.searchFile(jarFile, false)
		Assertions.assertThat(projectAndCommits.size).isEqualTo(1)
		Assertions.assertThat(projectAndCommits.first().project).isEqualTo("my-teamscale-project")
	}

	@Test
	fun testNoMultipleUploadsToSameProjectAndRevision() {
		val delayedTeamscaleMultiProjectUploader = DelayedTeamscaleMultiProjectUploader { project, revision ->
				val server = TeamscaleServer()
				server.project = project
				server.revision = revision!!.revision
				server.commit = revision.commit
				server
			}
		val locator = GitMultiProjectPropertiesLocator(
			delayedTeamscaleMultiProjectUploader, true, null
		)
		val jarFile = File(javaClass.getResource("multiple-same-target-git-properties-folder")!!.file)
		locator.searchFile(jarFile, false)
		val teamscaleServers = delayedTeamscaleMultiProjectUploader.teamscaleUploaders.map { it.teamscaleServer }
		Assertions.assertThat(teamscaleServers).hasSize(2)
		Assertions.assertThat(teamscaleServers).anyMatch { server ->
			server.project == "demo2" && server.commit == CommitDescriptor(
				"master",
				"1645713803000"
			)
		}
		Assertions.assertThat(teamscaleServers).anyMatch { server ->
			server.project == "demolib" && server.revision == "05b9d066a0c0762be622987de403b5752fa01cc0"
		}
	}
}
