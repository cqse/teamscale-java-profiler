package com.teamscale.jacoco.agent.upload.teamscale;

import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo;
import com.teamscale.jacoco.agent.options.ProjectAndCommit;
import com.teamscale.jacoco.agent.upload.DelayedMultiUploaderBase;
import com.teamscale.jacoco.agent.upload.IUploader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/** Wrapper for {@link TeamscaleUploader} that allows to upload the same coverage file to multiple Teamscale projects. */
public class DelayedTeamscaleMultiProjectUploader extends DelayedMultiUploaderBase implements IUploader {

	private final BiFunction<String, CommitInfo, TeamscaleServer> teamscaleServerFactory;
	private final List<TeamscaleUploader> teamscaleUploaders = new ArrayList<>();

	public DelayedTeamscaleMultiProjectUploader(
			BiFunction<String, CommitInfo, TeamscaleServer> teamscaleServerFactory) {
		this.teamscaleServerFactory = teamscaleServerFactory;
	}
	
	public List<TeamscaleUploader> getTeamscaleUploaders() {
		return teamscaleUploaders;
	}

	/**
	 * Adds a teamscale project and commit as a possible new target to upload coverage to. Checks if the project and
	 * commit are already registered as an upload target and will prevent duplicate uploads.
	 */
	public void addTeamscaleProjectAndCommit(File file, ProjectAndCommit projectAndCommit) {

		TeamscaleServer teamscaleServer = teamscaleServerFactory.apply(projectAndCommit.getProject(),
				projectAndCommit.getCommitInfo());

		if (this.teamscaleUploaders.stream().anyMatch(teamscaleUploader ->
				teamscaleUploader.teamscaleServer.hasSameProjectAndCommit(teamscaleServer)
		)) {
			logger.debug(
					"Project and commit in git.properties file {} are already registered as upload target. Coverage will not be uploaded multiple times to the same project {} and commit info {}.",
					file, projectAndCommit.getProject(), projectAndCommit.getCommitInfo());
			return;
		}
		TeamscaleUploader uploader = new TeamscaleUploader(teamscaleServer);
		teamscaleUploaders.add(uploader);
	}

	@Override
	protected Collection<IUploader> getWrappedUploaders() {
		return new ArrayList<>(teamscaleUploaders);
	}
}
