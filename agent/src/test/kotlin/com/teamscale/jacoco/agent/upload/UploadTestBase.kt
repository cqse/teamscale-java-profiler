package com.teamscale.jacoco.agent.upload

import com.teamscale.jacoco.agent.Agent
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.jacoco.CoverageFile
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths

/** Base class for tests regarding Teamscale/Artifactory uploads.  */
open class UploadTestBase {
	/** The url of the mockserver  */
	@JvmField
	protected var serverUrl: String = "/someUrl/"

	/** The mock server to run requests against.  */
	@JvmField
	protected var mockWebServer: MockWebServer? = null

	/** Uploader that will be set in child classes  */
	@JvmField
	var uploader: IUploader? = null

	/** The coverage file to test the upload with  */
	@JvmField
	var coverageFile: CoverageFile? = null

	/** Starts the mock server.  */
	@BeforeEach
	@Throws(Exception::class)
	fun setup(@TempDir tmpDir: File) {
		mockWebServer = MockWebServer()
		mockWebServer!!.start()
		val tmpFile = File("${tmpDir.path}${File.separator}tmpfile")
		tmpFile.createNewFile()
		coverageFile = CoverageFile(tmpFile)
	}

	/**
	 * After unsuccessfully uploading coverage, this method starts the agent which
	 * triggers the automatic upload retry of the remaining coverage.
	 */
	@Throws(UploaderException::class)
	protected fun startAgentAfterUploadFailure(options: AgentOptions) {
		options.setParentOutputDirectory(Paths.get(coverageFile.toString()).parent)
		mockWebServer!!.enqueue(MockResponse().setResponseCode(200))
		// Agent is started to check automatic upload retry.
		Agent(options, null)
	}

	/** Shuts down the mock server.  */
	@AfterEach
	@Throws(Exception::class)
	fun cleanup() {
		mockWebServer!!.shutdown()
	}
}
