package br.edu.devtitans.smartnotifications.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `preserves source text and adds anti-hallucination instruction`() {
        val prompt = promptBuilder.buildSummaryPrompt("Entrega na sexta-feira às 14h.")

        assertTrue(prompt.contains("Entrega na sexta-feira às 14h."))
        assertTrue(prompt.contains("Não invente informações"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank input`() {
        promptBuilder.buildSummaryPrompt("   ")
    }

    @Test
    fun `does not add source before trimming it`() {
        val prompt = promptBuilder.buildSummaryPrompt("  mensagem  ")

        assertTrue(prompt.contains("\nmensagem\n"))
        assertFalse(prompt.contains("  mensagem  "))
    }
}

