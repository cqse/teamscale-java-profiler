package com.teamscale.report.testwise.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.teamscale.client.CommitDescriptor
import java.io.Serializable

/** Revision information necessary for uploading reports to Teamscale.  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
	JsonSubTypes.Type(value = RevisionInfo.Commit::class, name = "COMMIT"),
	JsonSubTypes.Type(value = RevisionInfo.Revision::class, name = "REVISION")
)
sealed class RevisionInfo : Serializable {
	/** Commit descriptor in the format branch:timestamp.  */
	data class Commit(
		@JsonProperty("value") val value: String
	) : RevisionInfo()

	/** Source control revision, e.g. SVN revision or Git hash.  */
	data class Revision(
		@JsonProperty("value") val value: String?
	) : RevisionInfo()

	companion object {
		/**
		 * Creates a [RevisionInfo] from a commit descriptor or a revision string.
		 * If both are set, the commit wins. If both are null, returns [Revision] with null value.
		 */
		fun of(commit: CommitDescriptor?, revision: String?): RevisionInfo =
			if (commit != null) Commit(commit.toString())
			else Revision(revision)
	}
}
