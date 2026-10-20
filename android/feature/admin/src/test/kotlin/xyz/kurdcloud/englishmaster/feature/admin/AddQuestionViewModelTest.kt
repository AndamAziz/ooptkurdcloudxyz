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
import xyz.kurdcloud.englishmaster.core.model.MediaType

@OptIn(ExperimentalCoroutinesApi::class)
class AddQuestionViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun formIsCheckedBeforeAnythingIsSent() {
        val repo = FakeStaffRepository()
        val vm = AddQuestionViewModel(CefrLevel.A1, repo)
        vm.save()
        assertEquals(R.string.admin_add_need_prompt, vm.uiState.value.error)
        vm.update { it.copy(prompt = "Where is the ball?") }
        vm.setOption(0, "under the table")
        vm.save()
        assertEquals(R.string.admin_add_need_options, vm.uiState.value.error)
        vm.setOption(1, "Under the table")
        vm.save()
        assertEquals(R.string.admin_add_duplicate_options, vm.uiState.value.error)
        vm.setOption(1, "on the table")
        vm.save()
        assertEquals(R.string.admin_add_need_key, vm.uiState.value.error)
        vm.pickMedia(PickedMedia(MediaType.IMAGE, "image/png", ByteArray(AddQuestionViewModel.MAX_IMAGE + 1), "big.png"))
        assertEquals(R.string.admin_add_image_too_large, vm.uiState.value.error)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun mediaIsUploadedThenTheQuestionIsCreatedWithTheRightKey() {
        val repo = FakeStaffRepository()
        val vm = AddQuestionViewModel(CefrLevel.A2, repo)
        vm.update { it.copy(prompt = "Look at the picture. Where is the ball?", skill = "vocabulary", publish = false, caption = "A ball") }
        // A blank second field: the key (field 3) must still point at "under the table".
        vm.setOption(0, "on the table")
        vm.setOption(2, "under the table")
        vm.setOption(3, "in the box")
        vm.update { it.copy(correctIndex = 2) }
        vm.pickMedia(PickedMedia(MediaType.IMAGE, "image/png", ByteArray(10), "ball.png"))
        vm.save()

        assertTrue(vm.uiState.value.saved)
        assertEquals(listOf("upload:10:image/png", "create"), repo.calls)
        val q = repo.created!!
        assertEquals(listOf("on the table", "under the table", "in the box"), q.options)
        assertEquals(1, q.correctIndex)
        assertEquals("staff/m1.png", q.mediaPath)
        assertEquals(MediaType.IMAGE, q.mediaType)
        assertEquals(false, q.publish)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun publishingAllDraftsOfALevel() {
        val repo = FakeStaffRepository()
        val vm = QuestionsViewModel(repo)
        vm.load(CefrLevel.B1)
        assertEquals(1, vm.uiState.value.drafts)
        vm.publishAllDrafts()
        assertEquals(0, vm.uiState.value.drafts)
        assertEquals(R.string.admin_drafts_published, vm.uiState.value.message)
    }

    @Test
    fun editingLoadsTheQuestionAndKeepsItsPictureUnlessRemoved() {
        val repo = FakeStaffRepository()
        val vm = AddQuestionViewModel(CefrLevel.A1, repo, editId = "q1")
        val s = vm.uiState.value
        assertEquals(true, s.editing)
        assertEquals("I ___ a student.", s.prompt)
        assertEquals(listOf("is", "am", "are", ""), s.options)
        assertEquals(1, s.correctIndex)
        assertEquals("A boy", s.caption)

        vm.update { it.copy(prompt = "I ___ a teacher.") }
        vm.save()
        val (id, q) = repo.updated!!
        assertEquals("q1", id)
        assertEquals("I ___ a teacher.", q.prompt)
        assertEquals(listOf("is", "am", "are"), q.options)
        assertEquals(1, q.correctIndex)
        assertEquals("staff/p.png", q.mediaPath)
        assertEquals(MediaType.IMAGE, q.mediaType)
        assertEquals(true, vm.uiState.value.saved)
        assertEquals(false, "create" in repo.calls)

        val vm2 = AddQuestionViewModel(CefrLevel.A1, repo, editId = "q1")
        vm2.removeExistingMedia()
        vm2.save()
        assertEquals(null, repo.updated!!.second.mediaPath)
        assertEquals(null, repo.updated!!.second.mediaType)
    }

    @Test
    fun editingAMissingQuestionSaysSo() {
        val vm = AddQuestionViewModel(CefrLevel.A1, FakeStaffRepository(), editId = "gone")
        assertEquals(R.string.admin_edit_not_found, vm.uiState.value.error)
    }
}
