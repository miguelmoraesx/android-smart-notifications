package br.edu.devtitans.smartnotifications.ai

/** Builds task-specific prompts without coupling callers to a particular LLM runtime. */
class PromptBuilder {

    companion object {
        // Notification state changes are normally at the end of the stream. Bounding the prompt
        // prevents an accidental paste from consuming the model context and device memory.
        const val MAX_SOURCE_CHARS = 1_600
    }

    fun buildSummaryPrompt(sourceText: String): String {
        val normalizedText = sourceText
            .takeLast(MAX_SOURCE_CHARS * 2)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n")
            .takeMostRecent(MAX_SOURCE_CHARS)
        require(normalizedText.isNotEmpty()) { "O texto para resumo não pode estar vazio." }

        return buildString {
            appendLine("Resuma as notificações em português do Brasil, em um parágrafo de no máximo 25 palavras.")
            appendLine("Preserve nomes, datas, horários, prazos e ações atuais. Ignore repetições, ruído e fatos corrigidos.")
            appendLine("Não invente informações. Responda somente com o resumo, sem Markdown.")
            appendLine("<mensagens>")
            appendLine(normalizedText)
            append("</mensagens>")
        }
    }
}

private fun String.takeMostRecent(maxChars: Int): String {
    if (length <= maxChars) return this

    val tail = takeLast(maxChars)
    val firstCompleteLine = tail.indexOf('\n')
    return if (firstCompleteLine >= 0 && firstCompleteLine < tail.lastIndex) {
        tail.substring(firstCompleteLine + 1)
    } else {
        tail
    }
}
