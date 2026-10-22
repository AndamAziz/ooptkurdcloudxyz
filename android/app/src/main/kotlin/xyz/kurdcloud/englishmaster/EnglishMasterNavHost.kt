package xyz.kurdcloud.englishmaster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.EmailLink
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.model.LifeInUkChapter
import xyz.kurdcloud.englishmaster.core.model.LifeInUkTest
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.Topic
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.feature.admin.ActivityViewModel
import xyz.kurdcloud.englishmaster.feature.admin.CertificateRequestsViewModel
import xyz.kurdcloud.englishmaster.feature.admin.AddQuestionScreen
import xyz.kurdcloud.englishmaster.feature.admin.AddQuestionViewModel
import xyz.kurdcloud.englishmaster.feature.admin.QuestionsViewModel
import xyz.kurdcloud.englishmaster.feature.admin.StaffScreen
import xyz.kurdcloud.englishmaster.feature.admin.UsersViewModel
import xyz.kurdcloud.englishmaster.feature.auth.SetPasswordScreen
import xyz.kurdcloud.englishmaster.feature.auth.SetPasswordViewModel
import xyz.kurdcloud.englishmaster.feature.auth.AccountActions
import xyz.kurdcloud.englishmaster.feature.auth.AccountScreen
import xyz.kurdcloud.englishmaster.feature.auth.AccountViewModel
import xyz.kurdcloud.englishmaster.feature.auth.PrivacyScreen
import xyz.kurdcloud.englishmaster.feature.auth.AuthMessage
import xyz.kurdcloud.englishmaster.feature.auth.CheckEmailReason
import xyz.kurdcloud.englishmaster.feature.auth.CheckEmailScreen
import xyz.kurdcloud.englishmaster.feature.auth.ForgotPasswordScreen
import xyz.kurdcloud.englishmaster.feature.auth.ForgotPasswordViewModel
import xyz.kurdcloud.englishmaster.feature.auth.InterfaceLanguage
import xyz.kurdcloud.englishmaster.feature.auth.RegisterScreen
import xyz.kurdcloud.englishmaster.feature.auth.RegisterViewModel
import xyz.kurdcloud.englishmaster.feature.auth.SignInScreen
import xyz.kurdcloud.englishmaster.feature.auth.SignInViewModel
import xyz.kurdcloud.englishmaster.feature.auth.WelcomeScreen
import xyz.kurdcloud.englishmaster.feature.exam.CertificateExporter
import xyz.kurdcloud.englishmaster.feature.exam.CertificatesScreen
import xyz.kurdcloud.englishmaster.feature.exam.CertificatesViewModel
import xyz.kurdcloud.englishmaster.feature.exam.ExamScreen
import xyz.kurdcloud.englishmaster.feature.exam.ExamViewModel
import xyz.kurdcloud.englishmaster.feature.practice.HomeActions
import xyz.kurdcloud.englishmaster.feature.practice.LifeInUkActions
import xyz.kurdcloud.englishmaster.feature.practice.LifeInUkScreen
import xyz.kurdcloud.englishmaster.feature.practice.LifeInUkViewModel
import xyz.kurdcloud.englishmaster.feature.practice.MockTestScreen
import xyz.kurdcloud.englishmaster.feature.practice.MockTestViewModel
import xyz.kurdcloud.englishmaster.feature.practice.titleRes
import xyz.kurdcloud.englishmaster.feature.practice.R as PracticeR
import xyz.kurdcloud.englishmaster.feature.practice.HomeScreen
import xyz.kurdcloud.englishmaster.feature.practice.HomeViewModel
import xyz.kurdcloud.englishmaster.feature.practice.PracticeScreen
import xyz.kurdcloud.englishmaster.feature.practice.PracticeViewModel

private object Routes {
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign-in?notice={notice}"
    const val SET_PASSWORD = "set-password"
    const val REGISTER = "register"
    const val FORGOT = "forgot-password"
    const val CHECK_EMAIL = "check-email/{reason}"
    const val HOME = "home"
    const val PRACTICE = "practice/{level}?mistakes={mistakes}"
    const val TOPIC = "topic/{topic}"
    const val STAFF = "staff"
    const val ADD_QUESTION = "staff/new-question/{level}"
    const val EDIT_QUESTION = "staff/edit-question/{level}/{id}"
    const val EXAM = "exam/{level}"
    const val PLACEMENT = "placement"
    const val CERTIFICATES = "certificates"
    const val ACCOUNT = "account"
    const val PRIVACY = "privacy"
    const val LIFE_IN_UK = "life-in-uk"
    const val LIFE_IN_UK_CHAPTER = "life-in-uk/chapter/{tag}"
    const val LIFE_IN_UK_MOCK = "life-in-uk/mock-test"

    /** [notice]: a message to show on arrival (session expired, email link expired, ...). */
    fun signIn(notice: AuthMessage? = null) = "sign-in?notice=${notice?.name.orEmpty()}"
    fun checkEmail(reason: CheckEmailReason) = "check-email/${reason.name}"
    fun practice(level: CefrLevel, mistakes: Boolean = false) = "practice/${level.apiValue}?mistakes=$mistakes"
    fun exam(level: CefrLevel) = "exam/${level.apiValue}"
    fun topic(topic: Topic) = "topic/${topic.apiValue}"
    fun lifeInUkChapter(tag: String) = "life-in-uk/chapter/$tag"
}

@Composable
fun EnglishMasterNavHost(
    container: AppContainer,
    ready: StateFlow<Boolean>,
    currentLanguage: InterfaceLanguage,
    onLanguageSelected: (InterfaceLanguage) -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController(),
) {
    val isReady by ready.collectAsStateWithLifecycle()
    if (!isReady) {
        LoadingState()
        return
    }
    val session by container.auth.session.collectAsStateWithLifecycle()

    // A rejected refresh token signs the user out; send them to sign-in with an explanation.
    LaunchedEffect(Unit) {
        container.auth.sessionExpired.collect {
            navController.navigate(Routes.signIn(AuthMessage.SESSION_EXPIRED)) { popUpTo(0) }
        }
    }

    fun goHome() = navController.navigate(Routes.HOME) { popUpTo(0) }

    // Password-reset and sign-up confirmation emails open the app with tokens (see MainActivity).
    val emailLink by container.emailLinks.collectAsStateWithLifecycle()
    LaunchedEffect(emailLink) {
        val link = emailLink ?: return@LaunchedEffect
        container.emailLinks.value = null
        when (link) {
            is EmailLink.SignedIn -> when (val result = container.auth.completeEmailLink(link)) {
                is ApiResult.Success ->
                    if (link.recovery) navController.navigate(Routes.SET_PASSWORD) { popUpTo(0) } else goHome()
                is ApiResult.Failure -> navController.navigate(Routes.signIn(AuthMessage.from(result.error))) { popUpTo(0) }
            }
            EmailLink.Expired -> navController.navigate(Routes.signIn(AuthMessage.LINK_EXPIRED)) { popUpTo(0) }
            EmailLink.Invalid -> navController.navigate(Routes.signIn(AuthMessage.LINK_INVALID)) { popUpTo(0) }
        }
    }

    // Decided once; later sign-in/sign-out navigate explicitly instead of swapping the graph.
    val startDestination = remember { if (session != null) Routes.HOME else Routes.WELCOME }
    NavHost(navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onCreateAccount = { navController.navigate(Routes.REGISTER) },
                onSignIn = { navController.navigate(Routes.signIn()) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ACCOUNT) {
            val userId = session?.userId ?: return@composable
            val pending by remember(userId) { container.practice.observePendingCount(userId) }.collectAsStateWithLifecycle(0)
            val questionUpdate by container.questionUpdater.state.collectAsStateWithLifecycle()
            AccountScreen(
                viewModel = viewModel { AccountViewModel(container.auth, container.learning, container.account, container.staff) },
                versionName = BuildConfig.VERSION_NAME,
                pendingAnswers = pending,
                actions = AccountActions(
                    languageLabel = stringResource(
                        if (currentLanguage == InterfaceLanguage.SORANI) R.string.app_language_sorani else R.string.app_language_english,
                    ),
                    onSwitchLanguage = {
                        onLanguageSelected(if (currentLanguage == InterfaceLanguage.SORANI) InterfaceLanguage.ENGLISH else InterfaceLanguage.SORANI)
                    },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    checkForUpdatesLabel = onCheckForUpdates?.let { stringResource(R.string.update_check, BuildConfig.VERSION_NAME) },
                    onCheckForUpdates = onCheckForUpdates ?: {},
                    onUpdateQuestions = container.questionUpdater::updateAll,
                ),
                onSignedOut = { navController.navigate(Routes.WELCOME) { popUpTo(0) } },
                onBack = { navController.popBackStack() },
                questionUpdate = questionUpdate,
            )
        }
        composable(Routes.SIGN_IN) { entry ->
            val vm = viewModel { SignInViewModel(container.auth) }
            val notice = entry.arguments?.getString("notice")?.let { n -> AuthMessage.entries.firstOrNull { it.name == n } }
            LaunchedEffect(notice) { notice?.let(vm::showMessage) }
            SignInScreen(
                viewModel = vm,
                onSignedIn = ::goHome,
                onForgotPassword = { navController.navigate(Routes.FORGOT) },
                onCreateAccount = { navController.navigate(Routes.REGISTER) },
                onBack = { if (!navController.popBackStack()) navController.navigate(Routes.WELCOME) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = viewModel { RegisterViewModel(container.auth) },
                onRegistered = { outcome ->
                    when (outcome) {
                        RegisterOutcome.SIGNED_IN -> goHome()
                        RegisterOutcome.CONFIRM_EMAIL -> navController.navigate(Routes.checkEmail(CheckEmailReason.CONFIRM_ACCOUNT)) {
                            popUpTo(Routes.WELCOME)
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SET_PASSWORD) {
            SetPasswordScreen(
                viewModel = viewModel { SetPasswordViewModel(container.auth) },
                onDone = ::goHome,
                // Skipping keeps the user signed in with the old password unchanged.
                onCancel = ::goHome,
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                viewModel = viewModel { ForgotPasswordViewModel(container.auth) },
                onSent = { navController.navigate(Routes.checkEmail(CheckEmailReason.PASSWORD_RESET)) { popUpTo(Routes.WELCOME) } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CHECK_EMAIL) { entry ->
            val reason = CheckEmailReason.valueOf(entry.arguments?.getString("reason") ?: CheckEmailReason.CONFIRM_ACCOUNT.name)
            CheckEmailScreen(reason, onContinue = { navController.navigate(Routes.signIn()) { popUpTo(Routes.WELCOME) } })
        }
        composable(Routes.HOME) {
            val userId = session?.userId ?: return@composable
            val homeViewModel = viewModel {
                HomeViewModel(userId, container.content, container.practice, container.network, container.staff, container.learning, updater = container.questionUpdater)
            }
            HomeScreen(
                viewModel = homeViewModel,
                onOpenStaff = { navController.navigate(Routes.STAFF) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                onStartPractice = { navController.navigate(Routes.practice(it)) },
                actions = HomeActions(
                    onStartPractice = { navController.navigate(Routes.practice(it)) },
                    onReviewMistakes = { navController.navigate(Routes.practice(it, mistakes = true)) },
                    onTakeExam = { navController.navigate(Routes.exam(it)) },
                    onPlacementTest = { navController.navigate(Routes.PLACEMENT) },
                    onCertificates = { navController.navigate(Routes.CERTIFICATES) },
                    onPractiseTopic = { navController.navigate(Routes.topic(it)) },
                    onLifeInUk = { navController.navigate(Routes.LIFE_IN_UK) },
                ),
            )
        }
        composable(Routes.LIFE_IN_UK) {
            val userId = session?.userId ?: return@composable
            LifeInUkScreen(
                viewModel = viewModel { LifeInUkViewModel(userId, container.content, container.practice, container.network) },
                actions = LifeInUkActions(
                    onPractiseChapter = { navController.navigate(Routes.lifeInUkChapter(it.tag)) },
                    onPractiseAll = { navController.navigate(Routes.practice(CefrLevel.LIFE_IN_UK)) },
                    onReviewMistakes = { navController.navigate(Routes.practice(CefrLevel.LIFE_IN_UK, mistakes = true)) },
                    onMockTest = { navController.navigate(Routes.LIFE_IN_UK_MOCK) },
                    onBack = { navController.popBackStack() },
                ),
            )
        }
        composable(Routes.LIFE_IN_UK_CHAPTER) { entry ->
            val userId = session?.userId ?: return@composable
            val tag = entry.arguments?.getString("tag") ?: LifeInUkTest.TAG
            val chapter = LifeInUkChapter.fromTag(tag)
            PracticeScreen(
                level = CefrLevel.LIFE_IN_UK,
                viewModel = viewModel { PracticeViewModel(null, userId, container.content, container.practice, tag = tag) },
                onExit = { navController.popBackStack() },
                title = stringResource(chapter?.titleRes() ?: PracticeR.string.lituk_title),
            )
        }
        composable(Routes.LIFE_IN_UK_MOCK) {
            val userId = session?.userId ?: return@composable
            MockTestScreen(
                viewModel = viewModel { MockTestViewModel(userId, container.content, container.practice) },
                onClose = { navController.popBackStack() },
                onPractiseMistakes = {
                    navController.navigate(Routes.practice(CefrLevel.LIFE_IN_UK, mistakes = true)) { popUpTo(Routes.LIFE_IN_UK) }
                },
            )
        }
        composable(Routes.STAFF) {
            val userId = session?.userId ?: return@composable
            // Re-read roles from the server when the screen opens; tabs follow the result.
            var access by remember { mutableStateOf<StaffAccess?>(null) }
            LaunchedEffect(userId) {
                access = (container.staff.myAccess() as? ApiResult.Success)?.value ?: StaffAccess.NONE
            }
            val current = access
            if (current == null) {
                LoadingState()
            } else {
                StaffScreen(
                    access = current,
                    currentUserId = userId,
                    usersViewModel = { viewModel { UsersViewModel(container.staff) } },
                    questionsViewModel = { viewModel { QuestionsViewModel(container.staff) } },
                    activityViewModel = { viewModel { ActivityViewModel(container.staff) } },
                    onBack = { navController.popBackStack() },
                    onAddQuestion = { navController.navigate("staff/new-question/${it.apiValue}") },
                    onEditQuestion = { level, id -> navController.navigate("staff/edit-question/${level.apiValue}/$id") },
                    certificatesViewModel = { viewModel { CertificateRequestsViewModel(container.staff) } },
                )
            }
        }
        composable(Routes.EDIT_QUESTION) { entry ->
            val level = CefrLevel.fromApi(entry.arguments?.getString("level") ?: CefrLevel.A1.apiValue)
            val id = entry.arguments?.getString("id").orEmpty()
            AddQuestionScreen(
                viewModel = viewModel { AddQuestionViewModel(level, container.staff, editId = id) },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.ADD_QUESTION) { entry ->
            val level = CefrLevel.fromApi(entry.arguments?.getString("level") ?: CefrLevel.A1.apiValue)
            AddQuestionScreen(
                viewModel = viewModel { AddQuestionViewModel(level, container.staff) },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.PRACTICE) { entry ->
            val userId = session?.userId ?: return@composable
            val level = CefrLevel.fromApi(entry.arguments?.getString("level") ?: CefrLevel.A1.apiValue)
            val mistakes = entry.arguments?.getString("mistakes") == "true"
            PracticeScreen(
                level = level,
                viewModel = viewModel { PracticeViewModel(level, userId, container.content, container.practice, mistakesOnly = mistakes) },
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.TOPIC) { entry ->
            val userId = session?.userId ?: return@composable
            val topic = Topic.fromApi(entry.arguments?.getString("topic")) ?: Topic.LIFE_IN_UK
            PracticeScreen(
                level = null,
                viewModel = viewModel { PracticeViewModel(null, userId, container.content, container.practice, topic = topic) },
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.EXAM) { entry ->
            val level = CefrLevel.fromApi(entry.arguments?.getString("level") ?: CefrLevel.A1.apiValue)
            ExamScreen(
                viewModel = viewModel { ExamViewModel(ExamKind.LEVEL_FINAL, level, container.learning) },
                onClose = { navController.popBackStack() },
                onViewCertificates = { navController.navigate(Routes.CERTIFICATES) { popUpTo(Routes.HOME) } },
                onPractise = { navController.navigate(Routes.practice(it)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.PLACEMENT) {
            ExamScreen(
                viewModel = viewModel { ExamViewModel(ExamKind.PLACEMENT, null, container.learning) },
                onClose = { navController.popBackStack() },
                onViewCertificates = { navController.navigate(Routes.CERTIFICATES) { popUpTo(Routes.HOME) } },
                onPractise = { navController.navigate(Routes.practice(it)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.CERTIFICATES) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val verificationUrl = { c: xyz.kurdcloud.englishmaster.core.model.Certificate -> c.verificationUrl(container.config.baseUrl) }
            CertificatesScreen(
                viewModel = viewModel { CertificatesViewModel(container.learning) },
                exporter = remember { CertificateExporter(context.applicationContext, verificationUrl) },
                verificationUrl = verificationUrl,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
