package com.teamscale.maven;

import com.teamscale.client.EnvironmentVariableChecker;
import com.teamscale.client.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A base class for all Teamscale related maven Mojos. Offers basic attributes and functionality related to Teamscale
 * and Maven.
 */
public abstract class TeamscaleMojoBase extends AbstractMojo {

	/**
	 * The URL of the Teamscale instance to which the recorded coverage will be uploaded.
	 */
	@Parameter()
	public String teamscaleUrl;

	/**
	 * The Teamscale project to which the recorded coverage will be uploaded
	 */
	@Parameter()
	public String projectId;

	/**
	 * The username to use to perform the upload. Must have the "Upload external data" permission for the
	 * {@link #projectId}. Can also be specified via the Maven property {@code teamscale.username}.
	 */
	@Parameter(property = "teamscale.username")
	public String username;

	/**
	 * Teamscale access token of the {@link #username}. Can also be specified via the Maven property
	 * {@code teamscale.accessToken}.
	 */
	@Parameter(property = "teamscale.accessToken")
	public String accessToken;

	/**
	 * You can optionally use this property to override the code commit to which the coverage will be uploaded. Format:
	 * {@code BRANCH:UNIX_EPOCH_TIMESTAMP_IN_MILLISECONDS}
	 * <p>
	 * If no commit and revision is manually specified, the plugin will try to determine the currently checked-out Git
	 * commit. You should specify either commit or revision, not both. If both are specified, a warning is logged and
	 * the revision takes precedence.
	 */
	@Parameter(property = "teamscale.commit")
	public String commit;

	/**
	 * You can optionally use this property to override the revision to which the coverage will be uploaded. If no
	 * commit and revision is manually specified, the plugin will try to determine the current git revision. You should
	 * specify either commit or revision, not both. If both are specified, a warning is logged and the revision takes
	 * precedence.
	 */
	@Parameter(property = "teamscale.revision")
	public String revision;

	/**
	 * The repository id in your Teamscale project which Teamscale should use to look up the revision, if given. Null or
	 * empty will lead to a lookup in all repositories in the Teamscale project.
	 */
	@Parameter(property = "teamscale.repository")
	public String repository;

	/**
	 * Whether to skip the execution of this Mojo.
	 */
	@Parameter(defaultValue = "false")
	public boolean skip;

	/**
	 * The running Maven session. Provided automatically by Maven.
	 */
	@Parameter(defaultValue = "${session}", readonly = true)
	public MavenSession session;

	/**
	 * The resolved commit, either provided by the user or determined via the GitCommit class
	 */
	protected String resolvedCommit;

	/**
	 * The resolved revision, either provided by the user or determined via the GitCommit class
	 */
	protected String resolvedRevision;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!StringUtils.isEmpty(revision) && !StringUtils.isEmpty(commit)) {
			getLog().warn("Both revision and commit are set but only one of them is needed. " +
					"Teamscale will prefer the revision. If that's not intended, please do not set the revision manually.");
		}
	}

	/**
	 * Sets the <code>resolvedRevision</code> or <code>resolvedCommit</code>. If not provided, try to determine the
	 * revision from CI/CD environment variables via {@link EnvironmentVariableChecker} or from the Git repository via
	 * {@link GitCommitUtils}.
	 */
	protected void resolveCommitOrRevision() throws MojoFailureException {
		if (!StringUtils.isEmpty(revision)) {
			resolvedRevision = revision;
			return;
		}
		if (!StringUtils.isEmpty(commit)) {
			resolvedCommit = commit;
			return;
		}

		// Check CI/CD environment variables first
		String envCommit = EnvironmentVariableChecker.findCommit();
		if (envCommit != null) {
			resolvedRevision = envCommit;
			return;
		}

		// Fall back to Git repository detection
		Path basedir = session.getCurrentProject().getBasedir().toPath();
		try {
			resolvedRevision = GitCommitUtils.getGitHeadRevision(basedir);
		} catch (IOException e) {
			throw new MojoFailureException("There is no <revision> or <commit> configured in the pom.xml," +
					" no CI environment variable was found," +
					" and it was not possible to determine the current revision in " + basedir + " from Git", e);
		}
	}

	/**
	 * Retrieves the configuration of a goal execution for the given plugin
	 *
	 * @param pluginArtifact The id of the plugin
	 * @param pluginGoal     The name of the goal
	 * @return The configuration DOM if present, otherwise <code>null</code>
	 */
	protected Xpp3Dom getExecutionConfigurationDom(MavenProject project, String pluginArtifact, String pluginGoal) {
		Plugin plugin = project.getPlugin(pluginArtifact);
		if (plugin == null) {
			return null;
		}

		for (PluginExecution pluginExecution : plugin.getExecutions()) {
			if (pluginExecution.getGoals().contains(pluginGoal)) {
				return (Xpp3Dom) pluginExecution.getConfiguration();
			}
		}

		return null;
	}
}
