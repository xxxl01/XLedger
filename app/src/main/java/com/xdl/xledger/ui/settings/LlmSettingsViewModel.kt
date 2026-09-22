package com.xdl.xledger.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.remote.DEFAULT_LLM_BASE_URL
import com.xdl.xledger.data.remote.DEFAULT_LLM_MODEL
import com.xdl.xledger.data.remote.LlmConfigKeys
import com.xdl.xledger.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** LLM 配置页 UI 状态。 */
data class LlmSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val baseUrl: String = DEFAULT_LLM_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_LLM_MODEL,
    val enableThinking: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null,
)

class LlmSettingsViewModel(
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmSettingsUiState())
    val uiState: StateFlow<LlmSettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val baseUrl = configRepository.getValue(LlmConfigKeys.BASE_URL)
                val apiKey = configRepository.getValue(LlmConfigKeys.API_KEY)
                val model = configRepository.getValue(LlmConfigKeys.MODEL)
                val enableThinking = configRepository.getValue(LlmConfigKeys.ENABLE_THINKING) == "true"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        baseUrl = baseUrl ?: DEFAULT_LLM_BASE_URL,
                        apiKey = apiKey.orEmpty(),
                        model = model ?: DEFAULT_LLM_MODEL,
                        enableThinking = enableThinking,
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载 LLM 配置失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取 LLM 配置")
                }
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value) }
    }

    fun onEnableThinkingChange(value: Boolean) {
        _uiState.update { it.copy(enableThinking = value) }
    }

    /** 保存配置；任一字段为空时返回 false。 */
    suspend fun save(): Boolean {
        val state = _uiState.value
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        val apiKey = state.apiKey.trim()
        val model = state.model.trim()
        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Base URL、API Key 和模型都需要填写") }
            return false
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
        return try {
            configRepository.setConfig(LlmConfigKeys.BASE_URL, baseUrl)
            configRepository.setConfig(LlmConfigKeys.API_KEY, apiKey)
            configRepository.setConfig(LlmConfigKeys.MODEL, model)
            configRepository.setConfig(
                LlmConfigKeys.ENABLE_THINKING,
                if (state.enableThinking) "true" else "false",
            )
            _uiState.update {
                it.copy(isSaving = false, savedMessage = "LLM 配置已更新", baseUrl = baseUrl, model = model)
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "保存 LLM 配置失败", error)
            _uiState.update {
                it.copy(isSaving = false, errorMessage = error.message ?: "无法保存 LLM 配置")
            }
            false
        }
    }

    fun consumeSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "LlmSettingsViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                LlmSettingsViewModel(configRepository = application.configRepository)
            }
        }
    }
}
