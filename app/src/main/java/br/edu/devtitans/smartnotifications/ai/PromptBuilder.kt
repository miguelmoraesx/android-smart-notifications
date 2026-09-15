package br.edu.devtitans.smartnotifications.ai

/** Builds task-specific prompts without coupling callers to a particular LLM runtime. */
class PromptBuilder {

    fun buildSummaryPrompt(sourceText: String): String {
        val normalizedText = sourceText.trim()
        require(normalizedText.isNotEmpty()) { "O texto para resumo não pode estar vazio." }

        return """
            Você é um assistente que resume mensagens e notificações em português do Brasil.

            Regras:
            - Produza somente um parágrafo curto e objetivo.
            - Preserve nomes, datas, horários, prazos e ações importantes.
            - Não invente informações e não use listas ou formatação Markdown.
            - Una mensagens relacionadas em um resumo coerente.

            Texto de entrada:
            <mensagens>
            $normalizedText
            </mensagens>

            Resumo:
        """.trimIndent()
    }
}

