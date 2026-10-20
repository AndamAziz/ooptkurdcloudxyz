package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseServices(
    val auth: AuthApi,
    val practice: PracticeApi,
    val staff: StaffApi,
    val releases: ReleaseApi,
    val learning: LearningApi,
    /** Plain client for public downloads (no user token is sent). */
    val downloadClient: OkHttpClient,
    /** Base URL of public objects in a storage bucket, ending with '/'. */
    val publicStorageUrl: (bucket: String) -> String,
)

object NetworkFactory {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(config: SupabaseConfig, sessions: SessionProvider, base: OkHttpClient = OkHttpClient()): SupabaseServices {
        val common = base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(ApiKeyInterceptor(config.anonKey))
            .build()

        val auth = retrofit(config, common).create(AuthApi::class.java)
        val refresher = TokenRefresher(sessions, auth)
        val restClient = common.newBuilder()
            .addInterceptor(BearerTokenInterceptor(sessions, refresher))
            .authenticator(RefreshingAuthenticator(refresher))
            .build()
        val rest = retrofit(config, restClient)
        val downloads = base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return SupabaseServices(
            auth = auth,
            practice = rest.create(PracticeApi::class.java),
            staff = rest.create(StaffApi::class.java),
            releases = rest.create(ReleaseApi::class.java),
            learning = rest.create(LearningApi::class.java),
            downloadClient = downloads,
            publicStorageUrl = { bucket -> "${config.baseUrl}storage/v1/object/public/$bucket/" },
        )
    }

    private fun retrofit(config: SupabaseConfig, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(if (config.isConfigured) config.baseUrl else "https://invalid.localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
