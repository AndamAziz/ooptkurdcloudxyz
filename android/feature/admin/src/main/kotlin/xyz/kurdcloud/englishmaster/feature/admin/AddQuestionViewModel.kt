package xyz.kurdcloud.englishmaster.feature.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.NewQuestion
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

/** A picture or video chosen on the device, not yet uploaded. */
class PickedMedia(val type: MediaType, val mimeType: String, val bytes: ByteArray, val name: String)

data class AddQuestionUiState(
    val level: CefrLevel,
    val skill: String = "grammar",
    val passage: String = "",
    val prompt: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctIndex: Int? = null,
    val explanation: String = "",
    val media: PickedMedia? = null,
    val caption: String = "",
    val publish: Boolean = true,
    val saving: Boolean = false,
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
) {
    val filledOptions: List<String> get() = options.map { it.trim() }.filter { it.isNotEmpty() }
}

class AddQuestionViewModel(level: CefrLevel, private val staff: StaffRepository) : ViewModel() {
    private val state = MutableStateFlow(AddQuestionUiState(level))
    val uiState: StateFlow<AddQuestionUiState> = state.asStateFlow()

    fun update(transform: (AddQuestionUiState) -> AddQuestionUiState) = state.update { transform(it).copy(error = null) }

    fun setOption(index: Int, value: String) = update { s -> s.copy(options = s.options.mapIndexed { i, o -> if (i == index) value else o }) }

    fun pickMedia(media: PickedMedia?) {
        val limit = if (media?.type == MediaType.VIDEO) MAX_VIDEO else MAX_IMAGE
        if (media != null && media.bytes.size > limit) {
            state.update { it.copy(error = if (media.type == MediaType.VIDEO) R.string.admin_add_video_too_large else R.string.admin_add_image_too_large) }
            return
        }
        update { it.copy(media = media) }
    }

    /** Checks the form, uploads the media (if any), then creates the question. */
    fun save() {
        val s = state.value
        if (s.saving) return
        val problem = validate(s)
        if (problem != null) {
            state.update { it.copy(error = problem) }
            return
        }
        // The key must point into the non-empty options, in their original order.
        val filled = s.options.withIndex().filter { it.value.isNotBlank() }
        val correct = filled.indexOfFirst { it.index == s.correctIndex }
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val media = s.media
            val path = if (media == null) null else when (val up = staff.uploadMedia(media.bytes, media.mimeType)) {
                is ApiResult.Success -> up.value
                is ApiResult.Failure -> return@launch failed(up.error)
            }
            val question = NewQuestion(
                level = s.level,
                skill = s.skill,
                prompt = s.prompt,
                options = filled.map { it.value.trim() },
                correctIndex = correct,
                explanation = s.explanation,
                passage = s.passage,
                mediaType = media?.type,
                mediaPath = path,
                mediaCaption = s.caption.takeIf { media != null },
                publish = s.publish,
            )
            when (val r = staff.createQuestion(question)) {
                is ApiResult.Success -> state.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> failed(r.error)
            }
        }
    }

    private fun failed(error: ApiError) = state.update { it.copy(saving = false, error = error.message()) }

    private fun validate(s: AddQuestionUiState): Int? = when {
        s.prompt.isBlank() -> R.string.admin_add_need_prompt
        s.filledOptions.size < 2 -> R.string.admin_add_need_options
        s.filledOptions.map { it.lowercase() }.toSet().size != s.filledOptions.size -> R.string.admin_add_duplicate_options
        s.correctIndex == null || s.options.getOrNull(s.correctIndex).isNullOrBlank() -> R.string.admin_add_need_key
        else -> null
    }

    companion object {
        const val MAX_IMAGE = 5 * 1024 * 1024
        const val MAX_VIDEO = 25 * 1024 * 1024
    }
}
