package xyz.kurdcloud.englishmaster

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.SyncWorkerFactory

class EnglishMasterApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val readyState = MutableStateFlow(false)

    /** True once the stored session has been read; the splash screen waits for it. */
    val ready: StateFlow<Boolean> = readyState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.sessionStore.load()
            readyState.value = true
            // Deliver answers left over from earlier sessions.
            if (container.sessionStore.current() != null) container.syncScheduler.requestSync()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(syncer = { container.syncer }, awaitAppReady = { ready.first { it } }))
            .build()
}
