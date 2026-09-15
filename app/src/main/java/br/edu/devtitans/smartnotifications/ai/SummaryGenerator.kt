package br.edu.devtitans.smartnotifications.ai

import kotlinx.coroutines.flow.Flow

/**
 * Use case shared by this test screen and, in a future phase, NotificationViewer.
 * NotificationViewer will provide grouped notification text to this class; it will not know
 * anything about LiteRT-LM, model files, or prompt formatting.
 */
class SummaryGenerator(
    private val textGenerator: TextGenerator,
    private val promptBuilder: PromptBuilder,
) {
    fun generateSummary(sourceText: String): Flow<String> {
        val prompt = promptBuilder.buildSummaryPrompt(sourceText)
        return textGenerator.generate(prompt)
    }
}

