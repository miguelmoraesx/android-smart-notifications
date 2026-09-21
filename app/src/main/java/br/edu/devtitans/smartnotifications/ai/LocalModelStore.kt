package br.edu.devtitans.smartnotifications.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies a user-selected model to an absolute, app-private path required by LiteRT-LM. */
class LocalModelStore(private val context: Context) {
    private val modelDirectory = File(context.filesDir, "models")
    private val installedModel = File(modelDirectory, MODEL_FILE_NAME)

    fun getInstalledModel(): File? = installedModel.takeIf { it.isFile && it.length() > 0L }

    suspend fun import(uri: Uri): File = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        require(displayName?.endsWith(".litertlm", ignoreCase = true) != false) {
            "Selecione um arquivo de modelo com extensão .litertlm."
        }

        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Não foi possível criar o diretório privado de modelos."
        }

        val pendingFile = File(modelDirectory, "$MODEL_FILE_NAME.pending")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                pendingFile.outputStream().buffered(MODEL_COPY_BUFFER_BYTES).use { output ->
                    input.copyTo(output, MODEL_COPY_BUFFER_BYTES)
                }
            } ?: error("Não foi possível abrir o arquivo selecionado.")

            check(pendingFile.length() > 0L) { "O arquivo de modelo selecionado está vazio." }

            // POSIX rename atomically replaces an older model only after the new copy succeeds.
            Os.rename(pendingFile.absolutePath, installedModel.absolutePath)
            installedModel
        } finally {
            pendingFile.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
        }

    private companion object {
        const val MODEL_FILE_NAME = "selected-model.litertlm"
        const val MODEL_COPY_BUFFER_BYTES = 256 * 1024
    }
}
