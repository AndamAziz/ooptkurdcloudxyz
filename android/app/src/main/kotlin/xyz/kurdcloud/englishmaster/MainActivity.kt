package xyz.kurdcloud.englishmaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.kurdcloud.englishmaster.update.UpdateDialog
import xyz.kurdcloud.englishmaster.update.UpdateViewModel
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.EmailLink
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.TextToSpeechSpeaker
import xyz.kurdcloud.englishmaster.feature.auth.InterfaceLanguage
import java.util.Locale

class MainActivity : AppCompatActivity() {
    /** Reads questions aloud on request (speaker buttons); null until onCreate. */
    private var speaker: TextToSpeechSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as EnglishMasterApplication
        installSplashScreen().setKeepOnScreenCondition { !app.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleEmailLink(intent)
        val speaker = TextToSpeechSpeaker(this).also { speaker = it }
        setContent {
            EnglishMasterTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker) {
                    val updates = viewModel {
                        UpdateViewModel(app.container.updates, app.container.installer, BuildConfig.VERSION_CODE, BuildConfig.SELF_UPDATE)
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updates.onResume() }
                    val language = currentLanguage()
                    EnglishMasterNavHost(
                        container = app.container,
                        ready = app.ready,
                        currentLanguage = language,
                        onLanguageSelected = ::setLanguage,
                        onCheckForUpdates = if (BuildConfig.SELF_UPDATE) ({ updates.check(manual = true) }) else null,
                    )
                    val updateState by updates.uiState.collectAsStateWithLifecycle()
                    UpdateDialog(updateState, language, updates)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEmailLink(intent)
    }

    /** Passes a password-reset / confirmation link to the navigation (handled once). */
    private fun handleEmailLink(intent: Intent?) {
        val link = intent?.dataString?.let(EmailLink::parse) ?: return
        (application as EnglishMasterApplication).container.emailLinks.value = link
        intent.data = null
    }

    override fun onStop() {
        super.onStop()
        // Do not keep talking once the app is in the background.
        speaker?.stop()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    /**
     * Interface language is stored per app by AppCompat (system-managed on Android 13+,
     * autoStoreLocales below that). It is independent of the language being learned.
     */
    private fun currentLanguage(): InterfaceLanguage {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: Locale.getDefault().language
        return if (tag == InterfaceLanguage.SORANI.tag) InterfaceLanguage.SORANI else InterfaceLanguage.ENGLISH
    }

    private fun setLanguage(language: InterfaceLanguage) {
        if (language != currentLanguage()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
