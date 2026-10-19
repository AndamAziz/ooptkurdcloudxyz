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
