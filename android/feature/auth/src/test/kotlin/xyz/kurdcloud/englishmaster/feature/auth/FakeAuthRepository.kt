package xyz.kurdcloud.englishmaster.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.kurdcloud.englishmaster.core.data.AuthRepository
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.network.ApiResult

class FakeAuthRepository : AuthRepository {
    override val session = MutableStateFlow<AuthSession?>(null)
    override val sessionExpired = MutableSharedFlow<Unit>()
    var signInResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var registerResult: ApiResult<RegisterOutcome> = ApiResult.Success(RegisterOutcome.CONFIRM_EMAIL)
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    override suspend fun signIn(email: String, password: String): ApiResult<Unit> {
        calls += "signIn:$email"
        gate?.await()
        return signInResult
    }

    override suspend fun register(displayName: String, email: String, password: String): ApiResult<RegisterOutcome> {
        calls += "register:$displayName:$email"
        return registerResult
    }

    override suspend fun requestPasswordReset(email: String): ApiResult<Unit> {
        calls += "reset:$email"
        return ApiResult.Success(Unit)
    }

    var verifyRecoveryCodeResult: ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun verifyRecoveryCode(email: String, code: String): ApiResult<Unit> {
        calls += "verifyRecoveryCode:$email:$code"
        return verifyRecoveryCodeResult
    }

    var updatePasswordResult: ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun updatePassword(newPassword: String): ApiResult<Unit> {
        calls += "updatePassword"
        return updatePasswordResult
    }

    override suspend fun signOut() {
        calls += "signOut"
    }
}
