package xyz.kurdcloud.englishmaster

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import xyz.kurdcloud.englishmaster.core.data.AccountRepository
import xyz.kurdcloud.englishmaster.core.data.AndroidNetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.SupabaseAccountRepository
import xyz.kurdcloud.englishmaster.core.data.AuthRepository
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.KeystoreCipher
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.data.MediaFiles
import xyz.kurdcloud.englishmaster.core.data.SupabaseLearningRepository
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.OfflineFirstContentRepository
import xyz.kurdcloud.englishmaster.core.data.OfflineFirstPracticeRepository
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.data.ResponseSyncer
import xyz.kurdcloud.englishmaster.core.data.SessionStore
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.data.SupabaseStaffRepository
import xyz.kurdcloud.englishmaster.core.data.SupabaseUpdateRepository
import xyz.kurdcloud.englishmaster.core.data.UpdateRepository
import xyz.kurdcloud.englishmaster.update.ApkInstaller
import xyz.kurdcloud.englishmaster.update.PackageInstallerApkInstaller
import xyz.kurdcloud.englishmaster.core.data.SupabaseAuthRepository
import xyz.kurdcloud.englishmaster.core.data.SyncScheduler
import xyz.kurdcloud.englishmaster.core.data.WorkManagerSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.kurdcloud.englishmaster.core.database.EnglishMasterDatabase
import xyz.kurdcloud.englishmaster.core.model.EmailLink
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Manual dependency graph for the app process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val sessionStore = SessionStore(appContext.sessionDataStore, KeystoreCipher())
    private val services = NetworkFactory.create(config, sessionStore)
    private val database = EnglishMasterDatabase.create(appContext)

    val network: NetworkMonitor = AndroidNetworkMonitor(appContext)
    val syncer = ResponseSyncer(services.practice, database.responses(), sessionStore)
    val syncScheduler: SyncScheduler by lazy { WorkManagerSyncScheduler(WorkManager.getInstance(appContext)) }

    val auth: AuthRepository = SupabaseAuthRepository(config, services.auth, sessionStore)
    private val media = MediaFiles(services.downloadClient, services.publicStorageUrl("question-media"), appContext.filesDir.resolve("question-media"))
    val content: ContentRepository = OfflineFirstContentRepository(
        services.practice, database.questions(), database.levelPacks(), database.content(), media = media,
    )
    val staff: StaffRepository = SupabaseStaffRepository(services.staff, media)
    val learning: LearningRepository = SupabaseLearningRepository(services.learning, media)
    val updates: UpdateRepository = SupabaseUpdateRepository(
        services.releases,
        services.downloadClient,
        services.publicStorageUrl("app-releases"),
        appContext.cacheDir.resolve("updates"),
    )
    val installer: ApkInstaller = PackageInstallerApkInstaller(appContext)
    val account: AccountRepository = SupabaseAccountRepository(services.learning, auth) {
        database.clearAll()
        appContext.filesDir.resolve("question-media").deleteRecursively()
    }
    /** The latest email link (password reset / sign-up confirmation) opened with the app. */
    val emailLinks = MutableStateFlow<EmailLink?>(null)
    val practice: PracticeRepository =
        OfflineFirstPracticeRepository(database.responses(), syncer, { syncScheduler.requestSync() }, network)
}
