package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class AppReleaseDto(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_path") val apkPath: String,
    val sha256: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int,
    @SerialName("notes_en") val notesEn: String? = null,
    @SerialName("notes_ckb") val notesCkb: String? = null,
)

/** Published releases are readable without signing in (RLS: published rows only). */
interface ReleaseApi {
    @GET("rest/v1/app_releases")
    suspend fun latest(
        @Query("select") select: String = "version_code,version_name,apk_path,sha256,size_bytes,min_supported_version_code,notes_en,notes_ckb",
        @Query("published") published: String = "eq.true",
        @Query("order") order: String = "version_code.desc",
        @Query("limit") limit: Int = 1,
    ): List<AppReleaseDto>
}
