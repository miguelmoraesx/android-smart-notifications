package br.edu.devtitans.smartnotifications

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.edu.devtitans.smartnotifications.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Manual test screen for the isolated Android → LiteRT-LM → Gemma pipeline. */
class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val modelPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importAndInitializeModel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectModelButton.setOnClickListener {
            // LiteRT-LM requires a filesystem path, so LocalModelStore copies this document URI
            // to the app's private models directory before LLMManager initializes the engine.
            modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
        binding.summarizeButton.setOnClickListener {
            viewModel.summarize(binding.inputText.text.toString())
        }
        binding.cancelSummaryButton.setOnClickListener {
            viewModel.cancelSummary()
        }
        binding.unloadModelButton.setOnClickListener {
            viewModel.unloadModel()
        }
        binding.reloadModelButton.setOnClickListener {
            viewModel.reloadStoredModel()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: MainUiState) = with(binding) {
        statusText.text = state.modelStatus
        progressBar.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        selectModelButton.isEnabled = !state.isBusy
        inputText.isEnabled = !state.isBusy
        summarizeButton.isEnabled = state.modelReady && !state.isBusy
        cancelSummaryButton.visibility = if (state.isGenerating) View.VISIBLE else View.GONE
        unloadModelButton.visibility = if (state.modelReady) View.VISIBLE else View.GONE
        unloadModelButton.isEnabled = !state.isBusy
        reloadModelButton.visibility =
            if (!state.modelReady && state.hasStoredModel) View.VISIBLE else View.GONE
        reloadModelButton.isEnabled = !state.isBusy

        summaryText.text = state.summary.ifBlank { getString(R.string.summary_placeholder) }
        inferenceTimeText.text = state.lastInferenceDurationMs?.let { durationMs ->
            getString(R.string.inference_time, durationMs / 1_000.0)
        }.orEmpty()
        inferenceTimeText.visibility =
            if (state.lastInferenceDurationMs == null) View.GONE else View.VISIBLE
        errorText.text = state.errorMessage.orEmpty()
        errorText.visibility = if (state.errorMessage == null) View.GONE else View.VISIBLE
    }
}
