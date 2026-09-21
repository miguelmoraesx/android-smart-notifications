package br.edu.devtitans.smartnotifications.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        val prompt = promptBuilder.buildSummaryPrompt("  mensagem  \n\n  mais contexto  ")

        assertTrue(prompt.contains("\nmensagem\nmais contexto\n"))
        assertFalse(prompt.contains("  mensagem  "))
    }

    @Test
    fun `bounds input by keeping the most recent notifications`() {
        val oldLine = "antiga ".repeat(500)
        val recentLine = "João: horário final às 17h"

        val prompt = promptBuilder.buildSummaryPrompt("$oldLine\n$recentLine")
        val source = prompt.substringAfter("<mensagens>\n").substringBefore("\n</mensagens>")

        assertTrue(source.length <= PromptBuilder.MAX_SOURCE_CHARS)
        assertTrue(source.endsWith(recentLine))
        assertFalse(source.startsWith("antiga antiga"))
    }

    @Test
    fun `uses a compact output contract`() {
        val prompt = promptBuilder.buildSummaryPrompt("mensagem")

        assertTrue(prompt.contains("no máximo 25 palavras"))
        assertTrue(prompt.contains("Ignore repetições, ruído e fatos corrigidos"))
        assertEquals(1, "<mensagens>".toRegex().findAll(prompt).count())
    }

    @Test
    fun `removes exact duplicate notifications before inference`() {
        val prompt = promptBuilder.buildSummaryPrompt("alerta importante\nalerta importante")
        val source = prompt.substringAfter("<mensagens>\n").substringBefore("\n</mensagens>")

        assertEquals("alerta importante", source)
    }
}
