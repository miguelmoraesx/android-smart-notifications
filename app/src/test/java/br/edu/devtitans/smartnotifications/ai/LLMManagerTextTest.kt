package br.edu.devtitans.smartnotifications.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class LLMManagerTextTest {
    @Test
    fun `removes model preamble and markdown from final summary`() {
        val generated = """
            **Resumo:** A reunião será amanhã às 14h.

            Explicação desnecessária.
        """.trimIndent()

        assertEquals("A reunião será amanhã às 14h.", generated.sanitizeSummary())
    }

    @Test
    fun `joins wrapped lines in the first paragraph`() {
        val generated = "- Entrega confirmada\npara sexta-feira."

        assertEquals("Entrega confirmada para sexta-feira.", generated.sanitizeSummary())
    }
}
