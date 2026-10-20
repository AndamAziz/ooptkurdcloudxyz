package xyz.kurdcloud.englishmaster.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.network.ApiResult

data class CertificatesUiState(
    val loading: Boolean = true,
    val certificates: List<Certificate> = emptyList(),
    val problem: ExamProblem? = null,
)

class CertificatesViewModel(private val repo: LearningRepository) : ViewModel() {
    private val state = MutableStateFlow(CertificatesUiState())
    val uiState: StateFlow<CertificatesUiState> = state.asStateFlow()

    init { load() }

    fun load() {
        state.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            when (val r = repo.certificates()) {
                is ApiResult.Success -> state.update { CertificatesUiState(loading = false, certificates = r.value) }
                is ApiResult.Failure -> state.update { it.copy(loading = false, problem = ExamProblem.from(r.error)) }
            }
        }
    }
}
