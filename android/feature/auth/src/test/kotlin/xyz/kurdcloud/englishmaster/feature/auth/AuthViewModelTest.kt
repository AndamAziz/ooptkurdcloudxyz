package xyz.kurdcloud.englishmaster.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.AuthFailure

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val repo = FakeAuthRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sign in validates input before calling the server`() {
        val vm = SignInViewModel(repo)
        vm.onEmailChange("not-an-email")
        vm.submit()
        assertTrue(vm.uiState.value.emailError)
        assertTrue(vm.uiState.value.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `sign in shows loading then success and clears the password`() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        val vm = SignInViewModel(repo)
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("abcdef12")
        vm.submit()
        assertTrue(vm.uiState.value.submitting)
        vm.submit() // double tap is ignored while submitting
        gate.complete(Unit)

        assertTrue(vm.uiState.value.signedIn)
        assertFalse(vm.uiState.value.submitting)
        assertEquals("", vm.uiState.value.password)
        assertEquals(listOf("signIn:a@b.co"), repo.calls)
    }

    @Test
    fun `server errors become user-facing messages`() {
        repo.signInResult = ApiResult.Failure(ApiError.Auth(AuthFailure.INVALID_CREDENTIALS))
        val vm = SignInViewModel(repo)
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("x")
        vm.submit()
        assertEquals(AuthMessage.INVALID_CREDENTIALS, vm.uiState.value.message)

        repo.signInResult = ApiResult.Failure(ApiError.Network)
        vm.submit()
        assertEquals(AuthMessage.NETWORK, vm.uiState.value.message)
    }

    @Test
    fun `registration requires consent and a policy-compliant password`() {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Aras")
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("short")
        vm.submit()
        assertTrue(vm.uiState.value.passwordError)
        assertTrue(vm.uiState.value.consentError)
        assertTrue(repo.calls.isEmpty())

        vm.onPasswordChange("abcdef12")
        vm.onConsentChange(true)
        vm.submit()
        assertEquals(RegisterOutcome.CONFIRM_EMAIL, vm.uiState.value.outcome)
        assertEquals(listOf("register:Aras:a@b.co"), repo.calls)
    }

    @Test
    fun `password reset confirms without revealing whether the account exists`() {
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("nobody@example.com")
        vm.submit()
        assertTrue(vm.uiState.value.sent)
    }

    @Test
    fun `new password is checked, must be repeated, and server refusals are explained`() {
        val vm = SetPasswordViewModel(repo)
        vm.onPasswordChange("short")
        vm.submit()
        assertTrue(vm.uiState.value.passwordError)

        vm.onPasswordChange("newpass99")
        vm.onConfirmChange("newpass98")
        vm.submit()
        assertTrue(vm.uiState.value.mismatch)
        assertTrue(repo.calls.isEmpty())

        repo.updatePasswordResult = ApiResult.Failure(ApiError.Auth(AuthFailure.SAME_PASSWORD))
        vm.onConfirmChange("newpass99")
        vm.submit()
        assertEquals(AuthMessage.SAME_PASSWORD, vm.uiState.value.message)

        repo.updatePasswordResult = ApiResult.Success(Unit)
        vm.submit()
        assertTrue(vm.uiState.value.done)
        assertEquals("", vm.uiState.value.password)
    }
}
