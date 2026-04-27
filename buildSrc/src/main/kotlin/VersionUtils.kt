object VersionUtils {
	fun isTaggedRelease() = System.getenv()["GITHUB_REF"]?.contains("/tags/") ?: false
}