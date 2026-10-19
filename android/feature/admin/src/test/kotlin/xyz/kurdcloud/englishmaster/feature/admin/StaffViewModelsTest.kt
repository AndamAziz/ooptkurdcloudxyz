package xyz.kurdcloud.englishmaster.feature.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.network.ApiError

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelsTest {
    private val repo = FakeStaffRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `users load, search and role changes update the row`() {
        val vm = UsersViewModel(repo)
        assertEquals(2, vm.uiState.value.users.size)

        vm.onQueryChange("sara"); vm.load()
        assertEquals(listOf("Sara"), vm.uiState.value.users.map { it.displayName })

        vm.setRole(vm.uiState.value.users.single(), Role.TEACHER, granted = true)
        assertEquals(setOf(Role.STUDENT, Role.TEACHER), vm.uiState.value.users.single().roles)
        assertEquals(R.string.admin_saved, vm.uiState.value.message)
        assertNull(vm.uiState.value.busyUserId)
    }

    @Test
    fun `server refusal is shown and the row is unchanged`() {
        val vm = UsersViewModel(repo)
        val sara = vm.uiState.value.users.first { it.userId == "s1" }
        repo.failNext = ApiError.Forbidden
        vm.setSuspended(sara, true)
        assertEquals(R.string.admin_error_forbidden, vm.uiState.value.message)
        assertEquals(false, vm.uiState.value.users.first { it.userId == "s1" }.suspended)
    }

    @Test
    fun `load failure shows a retryable error`() {
        repo.failNext = ApiError.Network
        val vm = UsersViewModel(repo)
        assertEquals(R.string.admin_error_network, vm.uiState.value.error)
        vm.load()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `approving and retiring a question`() {
        val vm = QuestionsViewModel(repo)
        val q = vm.uiState.value.questions.single()

        vm.setApproved(q, true)
        assertEquals("approved", vm.uiState.value.questions.single().reviewStatus)
        vm.setRetired(vm.uiState.value.questions.single(), true)
        assertEquals("retired", vm.uiState.value.questions.single().status)
        assertEquals(listOf("review:q1:true:null", "review:q1:true:retired"), repo.calls)

        vm.load(CefrLevel.C2)
        assertTrue(vm.uiState.value.questions.isEmpty())
    }

    @Test
    fun `activity loads`() {
        assertEquals("Andam", ActivityViewModel(repo).uiState.value.entries.single().actorName)
    }
}
