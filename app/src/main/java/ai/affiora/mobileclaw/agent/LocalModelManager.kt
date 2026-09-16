package ai.affiora.mobileclaw.agent

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localModelDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_models")

/** Metadata for a downloadable on-device model. */
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val requiredRamMb: Long,
    val requiredStorageMb: Long,
)

/** Current state of a local model. */
sealed class ModelState {
    data class NotAvailable(val reason: String) : ModelState()
    data object NotDownloaded : ModelState()
    data class Downloading(val progress: Float) : ModelState()
    data class Downloaded(val path: String, val sizeBytes: Long) : ModelState()
    data class Error(val message: String) : ModelState()
}

/** Device hardware capabilities relevant to on-device inference. */
data class DeviceCapability(
    val apiLevel: Int,
    val apiLevelOk: Boolean,
    val totalRamMb: Long,
    val availableStorageMb: Long,
)

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MIN_API_LEVEL = 31

        /**
         * Special ID used for a user-imported .litertlm model.
         */
        const val CUSTOM_MODEL_ID = "custom-litertlm"

        val MODELS = listOf(
            LocalModelInfo(
                id = "gemma-4-e2b",
                displayName = "Gemma 4 E2B",
                huggingFaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
                fileName = "gemma-4-E2B-it.litertlm",
                fileSizeBytes = 2_580_000_000L,
                requiredRamMb = 6_000,
                requiredStorageMb = 3_600,
            ),
            LocalModelInfo(
                id = "gemma-4-e4b",
                displayName = "Gemma 4 E4B",
                huggingFaceRepo = "litert-community/gemma-4-E4B-it-litert-lm",
                fileName = "gemma-4-E4B-it.litertlm",
                fileSizeBytes = 3_650_000_000L,
                requiredRamMb = 8_000,
                requiredStorageMb = 4_700,
            ),
        )

        fun getModelInfo(modelId: String): LocalModelInfo? =
            MODELS.firstOrNull { it.id == modelId }
    }

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * User-imported models are stored separately from the official models.
     *
     * Example:
     * files/models/custom/my-model.litertlm
     */
    private val customModelsDir = File(modelsDir, "custom").also { it.mkdirs() }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    }

    private val _modelStates = mutableMapOf<String, MutableStateFlow<ModelState>>()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        for (model in MODELS) {
            _modelStates[model.id] = MutableStateFlow(computeInitialState(model))
        }

        // Register the custom model if one was already imported.
        _modelStates[CUSTOM_MODEL_ID] = MutableStateFlow(computeCustomModelState())
    }

    /** Observable state for a specific model. */
    fun getModelState(modelId: String): StateFlow<ModelState> =
        _modelStates.getOrPut(modelId) {
            val info = getModelInfo
