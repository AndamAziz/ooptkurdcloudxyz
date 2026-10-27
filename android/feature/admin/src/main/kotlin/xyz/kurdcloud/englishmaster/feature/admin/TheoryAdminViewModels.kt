package xyz.kurdcloud.englishmaster.feature.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.data.TheoryRepository
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.NewTheoryQuestion
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

/** A snackbar line: [text] is a plurals resource when [plural] is true, with [arg] as the quantity. */
data class StaffMessage(val text: Int, val arg: Int? = null, val plural: Boolean = false)

data class TheoryAdminUiState(
    val loading: Boolean = true,
    val sections: List<TheorySection> = emptyList(),
    /** Slug of the chapter whose drafts are being published, null when idle. */
    val publishing: String? = null,
    val message: StaffMessage? = null,
)

/**
 * The driving theory chapters as staff see them: how many questions each one publishes, a way to
 * publish the imported drafts, and the form for adding a question of their own.
 */
class TheoryAdminViewModel(
    private val theory: TheoryRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val transient = MutableStateFlow(Pair<String?, StaffMessage?>(null, null))
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<TheoryAdminUiState> = combine(
        theory.observeSections(),
        loading,
        transient,
    ) { sections, isLoading, (publishing, message) ->
        TheoryAdminUiState(
            loading = isLoading && sections.isEmpty(),
            sections = sections,
            publishing = publishing,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TheoryAdminUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = theory.refreshSections()
            loading.value = false
            if (result is ApiResult.Failure) transient.update { it.copy(second = StaffMessage(result.error.message())) }
        }
    }

    /** Publishes every draft question of one chapter (the imported ones arrive as drafts). */
    fun publishDrafts(slug: String) {
        if (transient.value.first != null) return
        transient.value = slug to null
        viewModelScope.launch {
            val message = when (val r = staff.publishTheoryDrafts(slug)) {
                is ApiResult.Success -> {
                    theory.refreshSections()
                    StaffMessage(R.plurals.admin_theory_published, r.value, plural = true)
                }
                is ApiResult.Failure -> StaffMessage(r.error.message())
            }
            transient.value = null to message
        }
    }

    fun messageShown() = transient.update { it.copy(second = null) }
}

data class AddTheoryQuestionUiState(
    val sectionSlug: String,
    val prompt: String = "",
    val promptKu: String = "",
    val answer: String = "",
    val answerKu: String = "",
    val explanation: String = "",
    val explanationKu: String = "",
    val media: PickedMedia? = null,
    val publish: Boolean = true,
    val saving: Boolean = false,
    @StringRes val error: Int? = null,
    /** Set when the question was saved; the screen then offers to add another. */
    val savedCount: Int = 0,
)

/**
 * Adds one theory question to a chapter. A teacher types only the question and the correct answer
 * (plus the Sorani translation and a picture if the question needs one) — the app makes the wrong
 * options itself from the other answers in the chapter.
 */
class AddTheoryQuestionViewModel(
    sectionSlug: String,
    private val staff: StaffRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AddTheoryQuestionUiState(sectionSlug))
    val uiState: StateFlow<AddTheoryQuestionUiState> = state.asStateFlow()

    fun update(transform: (AddTheoryQuestionUiState) -> AddTheoryQuestionUiState) =
        state.update { transform(it).copy(error = null) }

    fun pickMedia(media: PickedMedia?) {
        if (media != null && media.type != MediaType.IMAGE) {
            state.update { it.copy(error = R.string.admin_theory_image_only) }
            return
        }
        if (media != null && media.bytes.size > AddQuestionViewModel.MAX_IMAGE) {
            state.update { it.copy(error = R.string.admin_add_image_too_large) }
            return
        }
        update { it.copy(media = media) }
    }

    /** Uploads the picture (if any), then saves the question and clears the form for the next one. */
    fun save() {
        val s = state.value
        if (s.saving) return
        if (s.prompt.isBlank()) return state.update { it.copy(error = R.string.admin_add_need_prompt) }
        if (s.answer.isBlank()) return state.update { it.copy(error = R.string.admin_theory_need_answer) }
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val media = s.media
            val path = if (media == null) null else when (val up = staff.uploadMedia(media.bytes, media.mimeType)) {
                is ApiResult.Success -> up.value
                is ApiResult.Failure -> return@launch failed(up.error)
            }
            val question = NewTheoryQuestion(
                sectionSlug = s.sectionSlug,
                prompt = s.prompt,
                answer = s.answer,
                promptKu = s.promptKu,
                answerKu = s.answerKu,
                explanation = s.explanation,
                explanationKu = s.explanationKu,
                mediaPath = path,
                publish = s.publish,
            )
            when (val r = staff.addTheoryQuestion(question)) {
                is ApiResult.Success ->
                    // Teachers add questions in runs, so the form stays open with the chapter and
                    // the publish switch as they were.
                    state.value = AddTheoryQuestionUiState(
                        sectionSlug = s.sectionSlug,
                        publish = s.publish,
                        savedCount = s.savedCount + 1,
                    )
                is ApiResult.Failure -> failed(r.error)
            }
        }
    }

    private fun failed(error: ApiError) = state.update { it.copy(saving = false, error = error.message()) }
}
