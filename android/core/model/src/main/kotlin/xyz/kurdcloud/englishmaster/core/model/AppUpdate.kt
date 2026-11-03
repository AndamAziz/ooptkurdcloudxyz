package xyz.kurdcloud.englishmaster.core.model

/** A newer build published for in-app update (direct distribution only). */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    /** True when the installed version is below the release's minimum supported version. */
    val mandatory: Boolean,
    val notesEn: String?,
    val notesCkb: String?,
)

/** What a release changed, for the "What's new" page; [versionName] is the one it describes. */
data class ReleaseNotes(
    val versionName: String,
    val notesEn: String?,
    val notesCkb: String?,
) {
    /** The text to show, preferring the interface language and falling back to the other. */
    fun forSorani(sorani: Boolean): String? =
        (if (sorani) notesCkb ?: notesEn else notesEn ?: notesCkb)?.takeIf { it.isNotBlank() }
}
