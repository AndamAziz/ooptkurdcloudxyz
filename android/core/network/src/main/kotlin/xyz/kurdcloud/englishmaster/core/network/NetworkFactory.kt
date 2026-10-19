package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseServices(val auth: AuthApi, val practice: PracticeApi, val staff: StaffApi)

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
        return SupabaseServices(auth, rest.create(PracticeApi::class.java), rest.create(StaffApi::class.java))
    }

    private fun retrofit(config: SupabaseConfig, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(if (config.isConfigured) config.baseUrl else "https://invalid.localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
