package xyz.kurdcloud.englishmaster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
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
import xyz.kurdcloud.englishmaster.feature.practice.HomeScreen
import xyz.kurdcloud.englishmaster.feature.practice.HomeViewModel
import xyz.kurdcloud.englishmaster.feature.practice.PracticeScreen
import xyz.kurdcloud.englishmaster.feature.practice.PracticeViewModel

private object Routes {
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign-in?expired={expired}"
    const val REGISTER = "register"
    const val FORGOT = "forgot-password"
    const val CHECK_EMAIL = "check-email/{reason}"
    const val HOME = "home"
    const val PRACTICE = "practice/{level}?mistakes={mistakes}"
    const val TOPIC = "topic/{topic}"
    const val STAFF = "staff"
    const val ADD_QUESTION = "staff/new-question/{level}"
    const val EXAM = "exam/{level}"
    const val PLACEMENT = "placement"
    const val CERTIFICATES = "certificates"

    fun signIn(expired: Boolean = false) = "sign-in?expired=$expired"
    fun checkEmail(reason: CheckEmailReason) = "check-email/${reason.name}"
    fun practice(level: CefrLevel, mistakes: Boolean = false) = "practice/${level.apiValue}?mistakes=$mistakes"
    fun exam(level: CefrLevel) = "exam/${level.apiValue}"
    fun topic(topic: Topic) = "topic/${topic.apiValue}"
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
    val scope = rememberCoroutineScope()

    // A rejected refresh token signs the user out; send them to sign-in with an explanation.
    LaunchedEffect(Unit) {
        container.auth.sessionExpired.collect {
            navController.navigate(Routes.signIn(expired = true)) { popUpTo(0) }
        }
    }

    fun goHome() = navController.navigate(Routes.HOME) { popUpTo(0) }

    // Decided once; later sign-in/sign-out navigate explicitly instead of swapping the graph.
    val startDestination = remember { if (session != null) Routes.HOME else Routes.WELCOME }
    NavHost(navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onCreateAccount = { navController.navigate(Routes.REGISTER) },
                onSignIn = { navController.navigate(Routes.signIn()) },
            )
        }
        composable(Routes.SIGN_IN) { entry ->
            val vm = viewModel { SignInViewModel(container.auth) }
            val expired = entry.arguments?.getString("expired") == "true"
            LaunchedEffect(expired) { if (expired) vm.showMessage(AuthMessage.SESSION_EXPIRED) }
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
                HomeViewModel(userId, container.content, container.practice, container.network, container.staff, container.learning)
            }
            HomeScreen(
                viewModel = homeViewModel,
                onOpenStaff = { navController.navigate(Routes.STAFF) },
                checkForUpdatesLabel = onCheckForUpdates?.let { stringResource(R.string.update_check, BuildConfig.VERSION_NAME) },
                onCheckForUpdates = onCheckForUpdates ?: {},
                switchLanguageLabel = stringResource(
                    if (currentLanguage == InterfaceLanguage.SORANI) R.string.app_switch_to_english else R.string.app_switch_to_sorani,
                ),
                onSwitchLanguage = {
                    onLanguageSelected(if (currentLanguage == InterfaceLanguage.SORANI) InterfaceLanguage.ENGLISH else InterfaceLanguage.SORANI)
                },
                onStartPractice = { navController.navigate(Routes.practice(it)) },
                actions = HomeActions(
                    onStartPractice = { navController.navigate(Routes.practice(it)) },
                    onReviewMistakes = { navController.navigate(Routes.practice(it, mistakes = true)) },
                    onTakeExam = { navController.navigate(Routes.exam(it)) },
                    onPlacementTest = { navController.navigate(Routes.PLACEMENT) },
                    onCertificates = { navController.navigate(Routes.CERTIFICATES) },
                    onPractiseTopic = { navController.navigate(Routes.topic(it)) },
                ),
                onSignOut = {
                    scope.launch {
                        container.auth.signOut()
                        navController.navigate(Routes.WELCOME) { popUpTo(0) }
                    }
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
                    certificatesViewModel = { viewModel { CertificateRequestsViewModel(container.staff) } },
                )
            }
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
