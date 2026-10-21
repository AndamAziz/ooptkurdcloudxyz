package xyz.kurdcloud.englishmaster.core.data

import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.LearningApi
import xyz.kurdcloud.englishmaster.core.network.restCall

/** The learner's own account: deleting it (with everything stored for it) from inside the app. */
interface AccountRepository {
    /**
     * Deletes the account on the server, then wipes this device's copy (downloaded levels,
     * unsent answers, saved pictures) and signs out. Staff accounts are refused by the server
     * (hint "staff_account"): an admin removes the role first.
     */
    suspend fun deleteAccount(): ApiResult<Unit>
}

class SupabaseAccountRepository(
    private val api: LearningApi,
    private val auth: AuthRepository,
    private val clearLocalData: suspend () -> Unit,
) : AccountRepository {
    override suspend fun deleteAccount(): ApiResult<Unit> {
        val result = restCall { api.deleteMyAccount() }
        if (result is ApiResult.Success) {
            clearLocalData()
            auth.signOut()
        }
        return result
    }
}
