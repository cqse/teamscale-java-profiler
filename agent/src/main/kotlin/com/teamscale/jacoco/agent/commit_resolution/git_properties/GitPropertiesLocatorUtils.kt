package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.FileSystemUtils.listFilesRecursively
import com.teamscale.client.StringUtils.endsWithOneOf
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.jacoco.agent.options.ProjectAndCommit
import com.teamscale.report.util.BashFileSkippingInputStream
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.regex.Pattern

/** Utility methods to extract certain properties from git.properties files in archives and folders.  */
object GitPropertiesLocatorUtils {
	/** Name of the git.properties file.  */
	const val GIT_PROPERTIES_FILE_NAME: String = "git.properties"

	/** The git.properties key that holds the commit time.  */
	const val GIT_PROPERTIES_GIT_COMMIT_TIME: String = "git.commit.time"

	/** The git.properties key that holds the commit branch.  */
	const val GIT_PROPERTIES_GIT_BRANCH: String = "git.branch"

	/** The git.properties key that holds the commit hash.  */
	const val GIT_PROPERTIES_GIT_COMMIT_ID: String = "git.commit.id"

	/**
	 * Alternative git.properties key that might also hold the commit hash, depending on the Maven git-commit-id plugin
	 * configuration.
	 */
	const val GIT_PROPERTIES_GIT_COMMIT_ID_FULL: String = "git.commit.id.full"

	/**
	 * You can provide a teamscale timestamp in git.properties files to overwrite the revision. See [TS-38561](https://cqse.atlassian.net/browse/TS-38561).
	 */
	const val GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH: String = "teamscale.commit.branch"

	/**
	 * You can provide a teamscale timestamp in git.properties files to overwrite the revision. See [TS-38561](https://cqse.atlassian.net/browse/TS-38561).
	 */
	const val GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME: String = "teamscale.commit.time"

	/** The git.properties key that holds the Teamscale project name.  */
	const val GIT_PROPERTIES_TEAMSCALE_PROJECT: String = "teamscale.project"

	/** Matches the path to the jar file in a jar:file: URL in regex group 1.  */
	private val JAR_URL_REGEX: Pattern = Pattern.compile(
		"jar:(?:file|nested):(.*?)!.*",
		Pattern.CASE_INSENSITIVE
	)

	private val NESTED_JAR_REGEX: Pattern = Pattern.compile(
		"[jwea]ar:file:(.*?)\\*(.*)",
		Pattern.CASE_INSENSITIVE
	)

	/**
	 * Defined in [GitCommitIdMojo](https://github.com/git-commit-id/git-commit-id-maven-plugin/blob/ac05b16dfdcc2aebfa45ad3af4acf1254accffa3/src/main/java/pl/project13/maven/git/GitCommitIdMojo.java#L522)
	 */
	private const val GIT_PROPERTIES_DEFAULT_MAVEN_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"

	/**
	 * Defined in [GitPropertiesPlugin](https://github.com/n0mer/gradle-git-properties/blob/bb1c3353bb570495644b6c6c75e211296a8354fc/src/main/groovy/com/gorylenko/GitPropertiesPlugin.groovy#L68)
	 */
	private const val GIT_PROPERTIES_DEFAULT_GRADLE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ"

	/**
	 * Reads the git SHA1 and branch and timestamp from the given jar file's git.properties and builds a commit
	 * descriptor out of it. If no git.properties file can be found, returns null.
	 * 
	 * @throws IOException                   If reading the jar file fails.
	 * @throws InvalidGitPropertiesException If a git.properties file is found but it is malformed.
	 */
	@JvmStatic
	@Throws(IOException::class, InvalidGitPropertiesException::class)
	fun getCommitInfoFromGitProperties(
		file: File,
		isJarFile: Boolean,
		recursiveSearch: Boolean,
		gitPropertiesCommitTimeFormat: DateTimeFormatter?
	) = findGitPropertiesInFile(file, isJarFile, recursiveSearch).map { entryWithProperties ->
		getCommitInfoFromGitProperties(
			entryWithProperties.second, entryWithProperties.first, file,
			gitPropertiesCommitTimeFormat
		)
	}

	/**
	 * Tries to extract a file system path to a search root for the git.properties search. A search root is either a
	 * file system folder or a Jar file. If no such path can be extracted, returns null.
	 * 
	 * @throws URISyntaxException under certain circumstances if parsing the URL fails. This should be treated the same
	 * as a null search result but the exception is preserved so it can be logged.
	 */
	@JvmStatic
	@Throws(
		URISyntaxException::class,
		IOException::class,
		NoSuchMethodException::class,
		IllegalAccessException::class,
		InvocationTargetException::class
	)
	fun extractGitPropertiesSearchRoot(
		jarOrClassFolderUrl: URL
	): Pair<File, Boolean>? {
		val protocol = jarOrClassFolderUrl.protocol.lowercase(Locale.getDefault())
		when (protocol) {
			"file" -> {
				val jarOrClassFolderFile = File(jarOrClassFolderUrl.toURI())
				if (jarOrClassFolderFile.isDirectory() || isJarLikeFile(jarOrClassFolderUrl.path)) {
					return jarOrClassFolderFile to !jarOrClassFolderFile.isDirectory()
				}
			}

			"jar" -> {
				// Used e.g. by Spring Boot. Example: jar:file:/home/k/demo.jar!/BOOT-INF/classes!/
				val jarMatcher = JAR_URL_REGEX.matcher(jarOrClassFolderUrl.toString())
				if (jarMatcher.matches()) {
					return File(jarMatcher.group(1)) to true
				}
				// Used by some web applications and potentially fat jars.
				// Example: war:file:/Users/example/apache-tomcat/webapps/demo.war*/WEB-INF/lib/demoLib-1.0-SNAPSHOT.jar
				val nestedMatcher = NESTED_JAR_REGEX.matcher(jarOrClassFolderUrl.toString())
				if (nestedMatcher.matches()) {
					return File(nestedMatcher.group(1)) to true
				}
			}

			"war", "ear" -> {
				val nestedMatcher = NESTED_JAR_REGEX.matcher(jarOrClassFolderUrl.toString())
				if (nestedMatcher.matches()) {
					return File(nestedMatcher.group(1)) to true
				}
			}

			"vfs" -> return getVfsContentFolder(jarOrClassFolderUrl)
			else -> return null
		}
		return null
	}

	/**
	 * VFS (Virtual File System) protocol is used by JBoss EAP and Wildfly. Example of an URL:
	 * vfs:/content/helloworld.war/WEB-INF/classes
	 */
	@Throws(
		IOException::class,
		NoSuchMethodException::class,
		IllegalAccessException::class,
		InvocationTargetException::class
	)
	private fun getVfsContentFolder(
		jarOrClassFolderUrl: URL
	): Pair<File, Boolean> {
		// we obtain the URL of a specific class file as input, e.g.,
		// vfs:/content/helloworld.war/WEB-INF/classes
		// Next, we try to extract the artefact URL from it, e.g., vfs:/content/helloworld.war
		val artefactUrl = extractArtefactUrl(jarOrClassFolderUrl)

		val virtualFile = URL(artefactUrl).openConnection().getContent()
		val virtualFileClass: Class<*> = virtualFile.javaClass
		// obtain the physical location of the class file. It is created on demand in <jboss-installation-dir>/standalone/tmp/vfs
		val getPhysicalFileMethod = virtualFileClass.getMethod("getPhysicalFile")
		val file = getPhysicalFileMethod.invoke(virtualFile) as File
		return file to !file.isDirectory()
	}

	/**
	 * Extracts the artefact URL (e.g., vfs:/content/helloworld.war/) from the full URL of the class file (e.g.,
	 * vfs:/content/helloworld.war/WEB-INF/classes).
	 */
	private fun extractArtefactUrl(jarOrClassFolderUrl: URL): String {
		val url = jarOrClassFolderUrl.path.lowercase(Locale.getDefault())
		val pathSegments = url.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		val artefactUrlBuilder = StringBuilder("vfs:")
		var segmentIdx = 0
		while (segmentIdx < pathSegments.size) {
			val segment = pathSegments[segmentIdx]
			artefactUrlBuilder.append(segment)
			artefactUrlBuilder.append("/")
			if (isJarLikeFile(segment)) {
				break
			}
			segmentIdx += 1
		}
		if (segmentIdx == pathSegments.size) {
			return url
		}
		return artefactUrlBuilder.toString()
	}

	private fun isJarLikeFile(segment: String) = endsWithOneOf(
		segment.lowercase(Locale.getDefault()), ".jar", ".war", ".ear", ".aar"
	)

	/**
	 * Reads the 'teamscale.project' property values and the git SHA1s or branch + timestamp from all git.properties
	 * files contained in the provided folder or archive file.
	 * 
	 * @throws IOException                   If reading the jar file fails.
	 * @throws InvalidGitPropertiesException If a git.properties file is found but it is malformed.
	 */
	@JvmStatic
	@Throws(IOException::class, InvalidGitPropertiesException::class)
	fun getProjectRevisionsFromGitProperties(
		file: File, isJarFile: Boolean, recursiveSearch: Boolean,
		gitPropertiesCommitTimeFormat: DateTimeFormatter?
	) = findGitPropertiesInFile(
		file, isJarFile,
		recursiveSearch
	).map { entryWithProperties ->
		val commitInfo = getCommitInfoFromGitProperties(
			entryWithProperties.second,
			entryWithProperties.first, file, gitPropertiesCommitTimeFormat
		)
		val project = entryWithProperties.second.getProperty(GIT_PROPERTIES_TEAMSCALE_PROJECT)
		if (commitInfo.isEmpty && isEmpty(project)) {
			throw InvalidGitPropertiesException(
				"No entry or empty value for both '$GIT_PROPERTIES_GIT_COMMIT_ID'/'$GIT_PROPERTIES_GIT_COMMIT_ID_FULL' and '$GIT_PROPERTIES_TEAMSCALE_PROJECT' in $file.\nContents of $GIT_PROPERTIES_FILE_NAME: ${entryWithProperties.second}"
			)
		}
		ProjectAndCommit(project, commitInfo)
	}

	/**
	 * Returns pairs of paths to git.properties files and their parsed properties found in the provided folder or
	 * archive file. Nested jar files will also be searched recursively if specified.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun findGitPropertiesInFile(
		file: File, isJarFile: Boolean, recursiveSearch: Boolean
	): List<Pair<String, Properties>> {
		if (isJarFile) {
			return findGitPropertiesInArchiveFile(file, recursiveSearch)
		}
		return findGitPropertiesInDirectoryFile(file, recursiveSearch)
	}

	/**
	 * Searches for git properties in jar/war/ear/aar files
	 */
	@Throws(IOException::class)
	private fun findGitPropertiesInArchiveFile(
		file: File,
		recursiveSearch: Boolean
	): List<Pair<String, Properties>> {
		try {
			JarInputStream(
				BashFileSkippingInputStream(Files.newInputStream(file.toPath()))
			).use { jarStream ->
				return findGitPropertiesInArchive(jarStream, file.getName(), recursiveSearch)
			}
		} catch (e: IOException) {
			throw IOException(
				"Reading jar ${file.absolutePath} for obtaining commit descriptor from git.properties failed", e
			)
		}
	}

	/**
	 * Searches for git.properties file in the given folder
	 * 
	 * @param recursiveSearch If enabled, git.properties files will also be searched in jar files
	 */
	@Throws(IOException::class)
	private fun findGitPropertiesInDirectoryFile(
		directoryFile: File, recursiveSearch: Boolean
	): List<Pair<String, Properties>> {
		val result = findGitPropertiesInFolder(directoryFile).toMutableList()

		if (recursiveSearch) {
			result.addAll(findGitPropertiesInNestedJarFiles(directoryFile))
		}

		return result.toList()
	}

	/**
	 * Finds all jar files in the given folder and searches them recursively for git.properties
	 */
	@Throws(IOException::class)
	private fun findGitPropertiesInNestedJarFiles(directoryFile: File) =
		listFilesRecursively(directoryFile) {
			isJarLikeFile(it.getName())
		}.flatMap { jarFile ->
			val inputStream = JarInputStream(Files.newInputStream(jarFile.toPath()))
			val relativeFilePath = "${directoryFile.getName()}${File.separator}" + directoryFile.toPath()
				.relativize(jarFile.toPath())
			findGitPropertiesInArchive(inputStream, relativeFilePath, true)
		}

	/**
	 * Searches for git.properties files in the given folder
	 */
	@Throws(IOException::class)
	private fun findGitPropertiesInFolder(directoryFile: File) =
		listFilesRecursively(directoryFile) {
			it.getName().equals(GIT_PROPERTIES_FILE_NAME, ignoreCase = true)
		}.map { gitPropertiesFile ->
			try {
				Files.newInputStream(gitPropertiesFile.toPath()).use { inputStream ->
					val gitProperties = Properties()
					gitProperties.load(inputStream)
					val relativeFilePath = "${directoryFile.getName()}${File.separator}" + directoryFile.toPath()
						.relativize(gitPropertiesFile.toPath())
					relativeFilePath to gitProperties
				}
			} catch (e: IOException) {
				throw IOException(
					"Reading directory ${gitPropertiesFile.absolutePath} for obtaining commit descriptor from git.properties failed", e
				)
			}
		}

	/**
	 * Returns pairs of paths to git.properties files and their parsed properties found in the provided JarInputStream.
	 * Nested jar files will also be searched recursively if specified.
	 */
	@JvmStatic
	@JvmOverloads
	@Throws(IOException::class)
	fun findGitPropertiesInArchive(
		inputStream: JarInputStream,
		archiveName: String?,
		recursiveSearch: Boolean,
		isRootArchive: Boolean = true // Added flag to prevent nested crashes
	): MutableList<Pair<String, Properties>> {
		val result = mutableListOf<Pair<String, Properties>>()
		var isEmpty = true

		var entry = inputStream.nextJarEntry
		while (entry != null) {
			isEmpty = false
			val fullEntryName = if (archiveName.isNullOrEmpty()) entry.name else "$archiveName/${entry.name}"
			val fileName = entry.name.substringAfterLast('/')

			if (fileName.equals(GIT_PROPERTIES_FILE_NAME, ignoreCase = true)) {
				val gitProperties = Properties().apply { load(inputStream) }
				result.add(fullEntryName to gitProperties)

			} else if (recursiveSearch && isJarLikeFile(entry.name)) {
				val nestedJarStream = JarInputStream(inputStream)
				result.addAll(
					findGitPropertiesInArchive(nestedJarStream, fullEntryName,
						recursiveSearch = true,
						isRootArchive = false
					)
				)
			}
			entry = inputStream.nextJarEntry
		}

		if (isEmpty && isRootArchive) {
			throw IOException("No entries in Jar file $archiveName. Is this a valid jar file?. If so, please report to CQSE.")
		}

		return result
	}

	/**
	 * Returns the CommitInfo (revision and branch + timestmap) from a git properties file. The revision can be either
	 * in [.GIT_PROPERTIES_GIT_COMMIT_ID] or [.GIT_PROPERTIES_GIT_COMMIT_ID_FULL]. The branch and timestamp
	 * in [.GIT_PROPERTIES_GIT_BRANCH] + [.GIT_PROPERTIES_GIT_COMMIT_TIME] or in
	 * [.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH] + [.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME]. By default,
	 * times will be parsed with [.GIT_PROPERTIES_DEFAULT_GRADLE_DATE_FORMAT] and
	 * [.GIT_PROPERTIES_DEFAULT_MAVEN_DATE_FORMAT]. An additional format can be given with
	 * `dateTimeFormatter`
	 */
	@JvmStatic
	@Throws(InvalidGitPropertiesException::class)
	fun getCommitInfoFromGitProperties(
		gitProperties: Properties, entryName: String?, jarFile: File?,
		additionalDateTimeFormatter: DateTimeFormatter?
	): CommitInfo {
		val dateTimeFormatter = createDateTimeFormatter(additionalDateTimeFormatter)

		// Get Revision
		val revision = getRevisionFromGitProperties(gitProperties)

		// Get branch and timestamp from git.commit.branch and git.commit.id
		var commitDescriptor = getCommitDescriptorFromDefaultGitPropertyValues(
			gitProperties, entryName,
			jarFile, dateTimeFormatter
		)
		// When read from these properties, we should prefer to upload to the revision
		var preferCommitDescriptorOverRevision = false


		// Get branch and timestamp from teamscale.commit.branch and teamscale.commit.time (TS-38561)
		val teamscaleTimestampBasedCommitDescriptor = getCommitDescriptorFromTeamscaleTimestampProperty(
			gitProperties, entryName, jarFile, dateTimeFormatter
		)
		if (teamscaleTimestampBasedCommitDescriptor != null) {
			// In this case, as we specifically set this property, we should prefer branch and timestamp to the revision
			preferCommitDescriptorOverRevision = true
			commitDescriptor = teamscaleTimestampBasedCommitDescriptor
		}

		if (isEmpty(revision) && commitDescriptor == null) {
			throw InvalidGitPropertiesException(
				"No entry or invalid value for '" + GIT_PROPERTIES_GIT_COMMIT_ID + "', '" + GIT_PROPERTIES_GIT_COMMIT_ID_FULL +
						"', '" + GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH + "' and " + GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME + "'\n" +
						"Location: Entry '" + entryName + "' in jar file '" + jarFile + "'." +
						"\nContents of " + GIT_PROPERTIES_FILE_NAME + ":\n" + gitProperties
			)
		}

		val commitInfo = CommitInfo(revision, commitDescriptor)
		commitInfo.preferCommitDescriptorOverRevision = preferCommitDescriptorOverRevision
		return commitInfo
	}

	private fun createDateTimeFormatter(
		additionalDateTimeFormatter: DateTimeFormatter?
	): DateTimeFormatter {
		val defaultDateTimeFormatter = DateTimeFormatter.ofPattern(
			String.format(
				"[%s][%s]", GIT_PROPERTIES_DEFAULT_MAVEN_DATE_FORMAT,
				GIT_PROPERTIES_DEFAULT_GRADLE_DATE_FORMAT
			)
		)
		val builder = DateTimeFormatterBuilder().append(defaultDateTimeFormatter)
		if (additionalDateTimeFormatter != null) {
			builder.append(additionalDateTimeFormatter)
		}
		return builder.toFormatter()
	}

	private fun getRevisionFromGitProperties(gitProperties: Properties): String? {
		var revision = gitProperties.getProperty(GIT_PROPERTIES_GIT_COMMIT_ID)
		if (isEmpty(revision)) {
			revision = gitProperties.getProperty(GIT_PROPERTIES_GIT_COMMIT_ID_FULL)
		}
		return revision
	}

	@Throws(InvalidGitPropertiesException::class)
	private fun getCommitDescriptorFromTeamscaleTimestampProperty(
		gitProperties: Properties,
		entryName: String?,
		jarFile: File?,
		dateTimeFormatter: DateTimeFormatter
	): CommitDescriptor? {
		val teamscaleCommitBranch = gitProperties.getProperty(GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH)
		val teamscaleCommitTime = gitProperties.getProperty(GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME)

		if (isEmpty(teamscaleCommitBranch) || isEmpty(teamscaleCommitTime)) {
			return null
		}

		val teamscaleTimestampRegex = "\\d*(?:p\\d*)?"
		val teamscaleTimestampMatcher = Pattern.compile(teamscaleTimestampRegex).matcher(teamscaleCommitTime)
		if (teamscaleTimestampMatcher.matches()) {
			return CommitDescriptor(teamscaleCommitBranch, teamscaleCommitTime)
		}

		val epochTimestamp: Long
		try {
			epochTimestamp = ZonedDateTime.parse(teamscaleCommitTime, dateTimeFormatter).toInstant().toEpochMilli()
		} catch (e: DateTimeParseException) {
			throw InvalidGitPropertiesException(
				("Cannot parse commit time '" + teamscaleCommitTime + "' in the '" + GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME +
						"' property. It needs to be in the date formats '" + GIT_PROPERTIES_DEFAULT_MAVEN_DATE_FORMAT +
						"' or '" + GIT_PROPERTIES_DEFAULT_GRADLE_DATE_FORMAT + "' or match the Teamscale timestamp format '"
						+ teamscaleTimestampRegex + "'." +
						"\nLocation: Entry '" + entryName + "' in jar file '" + jarFile + "'." +
						"\nContents of " + GIT_PROPERTIES_FILE_NAME + ":\n" + gitProperties), e
			)
		}

		return CommitDescriptor(teamscaleCommitBranch, epochTimestamp)
	}

	@Throws(InvalidGitPropertiesException::class)
	private fun getCommitDescriptorFromDefaultGitPropertyValues(
		gitProperties: Properties,
		entryName: String?,
		jarFile: File?,
		dateTimeFormatter: DateTimeFormatter
	): CommitDescriptor? {
		val gitBranch = gitProperties.getProperty(GIT_PROPERTIES_GIT_BRANCH)
		val gitTime = gitProperties.getProperty(GIT_PROPERTIES_GIT_COMMIT_TIME)
		if (!isEmpty(gitBranch) && !isEmpty(gitTime)) {
			val gitTimestamp: Long
			try {
				gitTimestamp = ZonedDateTime.parse(gitTime, dateTimeFormatter).toInstant().toEpochMilli()
			} catch (e: DateTimeParseException) {
				throw InvalidGitPropertiesException(
					"Could not parse the timestamp in property '" + GIT_PROPERTIES_GIT_COMMIT_TIME + "'." +
							"\nLocation: Entry '" + entryName + "' in jar file '" + jarFile + "'." +
							"\nContents of " + GIT_PROPERTIES_FILE_NAME + ":\n" + gitProperties, e
				)
			}
			return CommitDescriptor(gitBranch, gitTimestamp)
		}
		return null
	}
}
