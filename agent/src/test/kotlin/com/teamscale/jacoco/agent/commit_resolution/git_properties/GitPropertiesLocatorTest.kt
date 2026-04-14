package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.findGitPropertiesInArchive
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.findGitPropertiesInFile
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.getCommitInfoFromGitProperties
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.jar.JarInputStream

class GitPropertiesLocatorTest {
	@Test
	@Throws(Exception::class)
	fun testReadingGitPropertiesFromArchive() {
		for (archiveName in TEST_ARCHIVES) {
			val jarInputStream = JarInputStream(javaClass.getResourceAsStream(archiveName))
			val commits = findGitPropertiesInArchive(jarInputStream, archiveName, true)
			Assertions.assertThat(commits.size).isEqualTo(1)
			val rev = getCommitInfoFromGitProperties(
				commits.first().second, "test",
				File("test.jar"), null
			).revision
			Assertions.assertThat(rev).withFailMessage("Wrong commit found in $archiveName")
				.isEqualTo("72c7b3f7e6c4802414283cdf7622e6127f3f8976")
		}
	}

	/**
	 * Checks if extraction of git.properties works for nested jar files.
	 */
	@Test
	@Throws(Exception::class)
	fun testReadingGitPropertiesFromNestedArchive() {
		val nestedArchiveFile = File(javaClass.getResource("nested-jar.war")!!.toURI())
		val commits = findGitPropertiesInFile(
			nestedArchiveFile, isJarFile = true, recursiveSearch = true
		)
		Assertions.assertThat(commits.size).isEqualTo(2) // First git.properties in the root war, 2nd in the nested Jar
		val rev = getCommitInfoFromGitProperties(
			commits[1].second,
			"test",
			File("test.jar"), null
		).revision
		Assertions.assertThat(rev).isEqualTo("5b3b2d44987be38f930fe57128274e317316423d")
	}

	@Test
	@Throws(Exception::class)
	fun testReadingGitPropertiesInJarFileNestedInFolder() {
		val folder = File(javaClass.getResource("multiple-git-properties-folder")!!.toURI())
		val commits = findGitPropertiesInFile(folder, isJarFile = false, recursiveSearch = true)
		Assertions.assertThat(commits.size).isEqualTo(2)
		val firstFind = commits.first()
		val secondFind = commits[1]
		Assertions.assertThat(firstFind.first).isEqualTo(
			"multiple-git-properties-folder${File.separator}WEB-INF${File.separator}classes${File.separator}git.properties"
		)
		Assertions.assertThat(secondFind.first).isEqualTo(
			"multiple-git-properties-folder${File.separator}WEB-INF${File.separator}lib${File.separator}demoLib-1.1-SNAPSHOT.jar${File.separator}git.properties"
		)
	}

	@Test
	fun testGitPropertiesWithInvalidTimestamp() {
		val gitProperties = Properties()
		gitProperties["git.commit.time"] = "123ab"
		gitProperties["git.branch"] = "master"
		Assertions.assertThatThrownBy {
			getCommitInfoFromGitProperties(
				gitProperties, "test",
				File("test.jar"), null
			)
		}.isInstanceOf(InvalidGitPropertiesException::class.java)
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testReadingTeamscaleTimestampFromProperties() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "42"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp)
		val commitInfo = getCommitInfoFromGitProperties(
			properties,
			"myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(timestamp)
		Assertions.assertThat(commitInfo.commit!!.branchName).isEqualTo(branchName)
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testTeamscaleTimestampIsOverwritingCommitBranchAndTime() {
		val properties = Properties()
		val teamscaleTimestampBranch = "myBranch1"
		val teamscaleTimestampTime = "42"
		val gitCommitBranch = "myBranch2"
		val gitCommitTime = "2024-05-13T16:42:03+02:00"
		properties.setProperty(
			GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH,
			teamscaleTimestampBranch
		)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, teamscaleTimestampTime)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, gitCommitBranch)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, gitCommitTime)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(teamscaleTimestampTime)
		Assertions.assertThat(commitInfo.commit!!.branchName).isEqualTo(teamscaleTimestampBranch)
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testCommitBranchAndTimeIsUsedIfNoTeamscaleTimestampIsGiven() {
		val properties = Properties()
		val gitCommitBranch = "myBranch2"
		val gitCommitTime = "2024-05-13T16:42:03+02:00"
		val epochTimestamp = "1715611323000"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, gitCommitBranch)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, gitCommitTime)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(epochTimestamp)
		Assertions.assertThat(commitInfo.commit!!.branchName).isEqualTo(gitCommitBranch)
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testTeamscaleTimestampCanContainLocalTime() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "2024-05-13T16:42:03+02:00"
		val epochTimestamp = "1715611323000"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(epochTimestamp)
		Assertions.assertThat(commitInfo.commit!!.branchName).isEqualTo(branchName)
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testRevisionAndTimestampAreBothReadIfPresent() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "2024-05-13T16:42:03+02:00"
		val revision = "ab1337cd"
		val epochTimestamp = "1715611323000"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_ID, revision)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(epochTimestamp)
		Assertions.assertThat(commitInfo.commit!!.branchName).isEqualTo(branchName)
		Assertions.assertThat(commitInfo.revision).isNotEmpty()
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testPreferCommitDescriptorOverRevisionIsSetWhenTeamscaleTimestampIsPresentInGitProperties() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "2024-05-13T16:42:03+02:00"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME, timestamp)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.preferCommitDescriptorOverRevision).isTrue()
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testPreferCommitDescriptorOverRevisionIsNotSetWhenTeamscaleTimestampIsNotPresentInGitProperties() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "2024-05-13T16:42:03+02:00"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, timestamp)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), null
		)
		Assertions.assertThat(commitInfo.preferCommitDescriptorOverRevision).isFalse()
	}

	@Test
	@Throws(InvalidGitPropertiesException::class)
	fun testAdditionalDateTimeFormatterIsUsed() {
		val properties = Properties()
		val branchName = "myBranch"
		val timestamp = "20240513T16:42:03+02:00"
		val epochTimestamp = "1715611323000"
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH, branchName)
		properties.setProperty(GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME, timestamp)
		val commitInfo = getCommitInfoFromGitProperties(
			properties, "myEntry", File("myJarFile"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ssXXX")
		)
		Assertions.assertThat(commitInfo.commit!!.timestamp).isEqualTo(epochTimestamp)
	}

	companion object {
		private val TEST_ARCHIVES = listOf(
			"plain-git-properties.jar", "spring-boot-git-properties.jar", "spring-boot-git-properties.war",
			"full-git-properties.jar", "spring-boot-3.jar"
		)
	}
}
