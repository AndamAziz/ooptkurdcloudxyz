package xyz.kurdcloud.englishmaster.core.network

/** Public client configuration. The anon key is public by design; RLS protects data. */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.startsWith("https://") && anonKey.isNotBlank()
    val baseUrl: String get() = url.trimEnd('/') + "/"

    override fun toString(): String = "SupabaseConfig(url=$url)"
}
