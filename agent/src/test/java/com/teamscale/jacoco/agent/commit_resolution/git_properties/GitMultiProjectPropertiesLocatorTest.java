package com.teamscale.jacoco.agent.commit_resolution.git_properties;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.options.ProjectAndCommit;
import com.teamscale.jacoco.agent.upload.teamscale.DelayedTeamscaleMultiProjectUploader;
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GitMultiProjectPropertiesLocatorTest {
	@Test
	void testNoErrorIsThrownWhenGitPropertiesFileDoesNotHaveAProject() {

		List<ProjectAndCommit> projectAndCommits = new ArrayList<>();
		GitMultiProjectPropertiesLocator locator = new GitMultiProjectPropertiesLocator(
				new DelayedTeamscaleMultiProjectUploader((project, revision) -> {
					projectAndCommits.add(new ProjectAndCommit(project, revision));
					return new TeamscaleServer();
				}), true, null);
		File jarFile = new File(getClass().getResource("emptyTeamscaleProjectGitProperties").getFile());
		locator.searchFile(jarFile, false);
		assertThat(projectAndCommits.size()).isEqualTo(1);
		assertThat(projectAndCommits.get(0).getProject()).isEqualTo("my-teamscale-project");
	}

	@Test
	void testNoMultipleUploadsToSameProjectAndRevision() {
		DelayedTeamscaleMultiProjectUploader delayedTeamscaleMultiProjectUploader = new DelayedTeamscaleMultiProjectUploader(
				(project, revision) -> {
					TeamscaleServer server = new TeamscaleServer();
					server.project = project;
					server.revision = revision.revision;
					server.commit = revision.commit;
					return server;
				});
		GitMultiProjectPropertiesLocator locator = new GitMultiProjectPropertiesLocator(
				delayedTeamscaleMultiProjectUploader, true, null
		);
		File jarFile = new File(getClass().getResource("multiple-same-target-git-properties-folder").getFile());
		locator.searchFile(jarFile, false);
		List<TeamscaleServer> teamscaleServers = delayedTeamscaleMultiProjectUploader.getTeamscaleUploaders().stream()
				.map(t -> t.teamscaleServer).collect(Collectors.toList());
		assertThat(teamscaleServers).hasSize(2);
		assertThat(teamscaleServers).anyMatch(server -> server.project.equals("demo2") && server.commit.equals(
				new CommitDescriptor("master", "1645713803000")));
		assertThat(teamscaleServers).anyMatch(server -> server.project.equals("demolib") && server.revision.equals(
				"05b9d066a0c0762be622987de403b5752fa01cc0"));
	}

}
