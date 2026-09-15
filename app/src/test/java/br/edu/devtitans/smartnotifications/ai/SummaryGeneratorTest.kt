package br.edu.devtitans.smartnotifications.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGeneratorTest {
    @Test
    fun `builds prompt and delegates generation without mocking a model response in production`() =
        runBlocking {
            var receivedPrompt = ""
            val generator = TextGenerator { prompt ->
                receivedPrompt = prompt
                flowOf("resumo de teste")
            }
            val subject = SummaryGenerator(generator, PromptBuilder())

            assertEquals("resumo de teste", subject.generateSummary("mensagem").first())
            assertTrue(receivedPrompt.contains("<mensagens>\nmensagem\n</mensagens>"))
        }
}

