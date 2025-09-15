package com.teamscale.jacoco.agent.commit_resolution.git_properties;

import kotlin.Pair;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GitPropertiesLocatorTest {

	private static final List<String> TEST_ARCHIVES = Arrays
			.asList("plain-git-properties.jar", "spring-boot-git-properties.jar", "spring-boot-git-properties.war",
					"full-git-properties.jar", "spring-boot-3.jar");

	@Test
	public void testReadingGitPropertiesFromArchive() throws Exception {
		for (String archiveName : TEST_ARCHIVES) {
			JarInputStream jarInputStream = new JarInputStream(getClass().getResourceAsStream(archiveName));
			List<Pair<String, Properties>> commits = GitPropertiesLocatorUtils
					.findGitPropertiesInArchive(jarInputStream, archiveName, true);
			assertThat(commits.size()).isEqualTo(1);
			String rev = GitPropertiesLocatorUtils
					.getCommitInfoFromGitProperties(commits.get(0).getSecond(), "test",
							new File("test.jar"), null).revision;
			assertThat(rev).withFailMessage("Wrong commit found in " + archiveName)
					.isEqualTo("72c7b3f7e6c4802414283cdf7622e6127f3f8976");
		}
	}

	/**
	 * Checks if extraction of git.properties works for nested jar files.
	 */
	@Test
	public void testReadingGitPropertiesFromNestedArchive() throws Exception {
		File nestedArchiveFile = new File(getClass().getResource("nested-jar.war").toURI());
		List<Pair<String, Properties>> commits = GitPropertiesLocatorUtils.findGitPropertiesInFile(nestedArchiveFile,
				true, true);
		assertThat(commits.size()).isEqualTo(2); // First git.properties in the root war, 2nd in the nested Jar
		String rev = GitPropertiesLocatorUtils
				.getCommitInfoFromGitProperties(commits.get(1).getSecond(),
						"test",
						new File("test.jar"), null).revision;
		assertThat(rev).isEqualTo("5b3b2d44987be38f930fe57128274e317316423d");
	}

	@Test
	public void testReadingGitPropertiesInJarFileNestedInFolder() throws Exception {
		File folder = new File(getClass().getResource("multiple-git-properties-folder").toURI());
		List<Pair<String, Properties>> commits = GitPropertiesLocatorUtils.findGitPropertiesInFile(folder, false, true);
		assertThat(commits.size()).isEqualTo(2);
		Pair<String, Properties> firstFind = commits.get(0);
		Pair<String, Properties> secondFind = commits.get(1);
		assertThat(firstFind.getFirst()).isEqualTo(
				"multiple-git-properties-folder" + File.separator + "WEB-INF" + File.separator + "classes" +
						File.separator + "git.properties");
		assertThat(secondFind.getFirst()).isEqualTo(
				"multiple-git-properties-folder" + File.separator + "WEB-INF" + File.separator + "lib" +
						File.separator + "demoLib-1.1-SNAPSHOT.jar" + File.separator + "git.properties");
	}

	@Test
	public void testGitPropertiesWithInvalidTimestamp() {
		Properties gitProperties = new Properties();
		gitProperties.put("git.commit.time", "123ab");
		gitProperties.put("git.branch", "master");
		assertThatThrownBy(
				() -> GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(gitProperties, "test",
						new File("test.jar"), null))
				.isInstanceOf(InvalidGitPropertiesException.class);
	}

	@Test
	public void testReadingTeamscaleTimestampFromProperties() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "42";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.commit.timestamp).isEqualTo(timestamp);
		assertThat(commitInfo.commit.branchName).isEqualTo(branchName);
	}

	@Test
	public void testTeamscaleTimestampIsOverwritingCommitBranchAndTime() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String teamscaleTimestampBranch = "myBranch1";
		String teamscaleTimestampTime = "42";
		String gitCommitBranch = "myBranch2";
		String gitCommitTime = "2024-05-13T16:42:03+02:00";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH,
				teamscaleTimestampBranch);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, teamscaleTimestampTime);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, gitCommitBranch);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, gitCommitTime);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.commit.timestamp).isEqualTo(teamscaleTimestampTime);
		assertThat(commitInfo.commit.branchName).isEqualTo(teamscaleTimestampBranch);
	}

	@Test
	public void testCommitBranchAndTimeIsUsedIfNoTeamscaleTimestampIsGiven() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String gitCommitBranch = "myBranch2";
		String gitCommitTime = "2024-05-13T16:42:03+02:00";
		String epochTimestamp = "1715611323000";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, gitCommitBranch);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, gitCommitTime);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.commit.timestamp).isEqualTo(epochTimestamp);
		assertThat(commitInfo.commit.branchName).isEqualTo(gitCommitBranch);
	}

	@Test
	public void testTeamscaleTimestampCanContainLocalTime() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "2024-05-13T16:42:03+02:00";
		String epochTimestamp = "1715611323000";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.commit.timestamp).isEqualTo(epochTimestamp);
		assertThat(commitInfo.commit.branchName).isEqualTo(branchName);
	}

	@Test
	public void testRevisionAndTimestampAreBothReadIfPresent() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "2024-05-13T16:42:03+02:00";
		String revision = "ab1337cd";
		String epochTimestamp = "1715611323000";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_ID, revision);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.commit.timestamp).isEqualTo(epochTimestamp);
		assertThat(commitInfo.commit.branchName).isEqualTo(branchName);
		assertThat(commitInfo.revision).isNotEmpty();
	}

	@Test
	public void testPreferCommitDescriptorOverRevisionIsSetWhenTeamscaleTimestampIsPresentInGitProperties() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "2024-05-13T16:42:03+02:00";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.preferCommitDescriptorOverRevision).isTrue();
	}

	@Test
	public void testPreferCommitDescriptorOverRevisionIsNotSetWhenTeamscaleTimestampIsNotPresentInGitProperties() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "2024-05-13T16:42:03+02:00";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, timestamp);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), null);
		assertThat(commitInfo.preferCommitDescriptorOverRevision).isFalse();
	}

	@Test
	public void testAdditionalDateTimeFormatterIsUsed() throws InvalidGitPropertiesException {
		Properties properties = new Properties();
		String branchName = "myBranch";
		String timestamp = "20240513T16:42:03+02:00";
		String epochTimestamp = "1715611323000";
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, branchName);
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, timestamp);
		CommitInfo commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(properties,
				"myEntry", new File("myJarFile"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ssXXX"));
		assertThat(commitInfo.commit.timestamp).isEqualTo(epochTimestamp);
	}
}
