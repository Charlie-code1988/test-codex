package com.example.financeapp.data

import android.content.Context
import android.net.Uri
import com.example.financeapp.db.DocumentDao
import com.example.financeapp.db.DocumentEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao
) {

    fun observeDocuments(): Flow<List<Document>> {
        return dao.observeAll().map { entities -> entities.map { it.toModel() } }
    }

    fun observeDocumentById(documentId: Long): Flow<Document?> {
        return dao.observeById(documentId).map { it?.toModel() }
    }

    suspend fun importDocument(originalUri: String): ImportResult {
        val existing = dao.findByOriginalUri(originalUri)
        if (existing != null) {
            return ImportResult.Duplicate(existing.toModel())
        }

        val persistedAppUri = copyToAppPrivateStorage(originalUri)
        dao.insert(
            DocumentEntity(
                originalUri = originalUri,
                appUri = persistedAppUri,
                createdAt = System.currentTimeMillis(),
                status = "IMPORTED"
            )
        )
        return ImportResult.Success
    }

    suspend fun runOcr(documentId: Long): OcrResult {
        val document = dao.getById(documentId) ?: return OcrResult.DocumentNotFound

        dao.updateOcrResult(
            documentId = documentId,
            ocrStatus = OcrStatuses.RUNNING,
            latinRawText = document.latinRawText,
            chineseRawText = document.chineseRawText,
            finalOcrText = document.finalOcrText,
            ocrRawText = document.ocrRawText,
            ocrUpdatedAt = System.currentTimeMillis()
        )

        return runCatching {
            val image = InputImage.fromFilePath(context, Uri.parse(document.appUri))

            val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

            val latinText = latinRecognizer.process(image).await().text.orEmpty()
            val chineseText = chineseRecognizer.process(image).await().text.orEmpty()

            val finalText = buildFinalOcrText(latinText, chineseText)

            dao.updateOcrResult(
                documentId = documentId,
                ocrStatus = OcrStatuses.SUCCESS,
                latinRawText = latinText,
                chineseRawText = chineseText,
                finalOcrText = finalText,
                ocrRawText = finalText,
                ocrUpdatedAt = System.currentTimeMillis()
            )
            OcrResult.Success
        }.getOrElse { throwable ->
            dao.updateOcrResult(
                documentId = documentId,
                ocrStatus = OcrStatuses.FAILED,
                latinRawText = null,
                chineseRawText = null,
                finalOcrText = throwable.message,
                ocrRawText = throwable.message,
                ocrUpdatedAt = System.currentTimeMillis()
            )
            OcrResult.Failed(throwable.message ?: "OCR 失败")
        }
    }

    private fun buildFinalOcrText(latinText: String, chineseText: String): String {
        val latinCleanBase = cleanFinalText(latinText, preferChinese = false)
        val chineseCleanBase = cleanFinalText(chineseText, preferChinese = true)

        val chineseRatioInChinese = chineseCharRatio(chineseCleanBase)
        val latinUsefulRatio = latinUsefulRatio(latinCleanBase)
        val latinGarbageRatio = latinGarbageTokenRatio(latinCleanBase)

        val shouldPreferChinese = chineseRatioInChinese >= 0.25 && latinGarbageRatio >= 0.35
        val shouldPreferLatin = latinUsefulRatio >= 0.55 && chineseRatioInChinese < 0.10

        return when {
            shouldPreferChinese -> chineseCleanBase
            shouldPreferLatin -> latinCleanBase
            else -> cleanFinalText(fuseMixedTexts(latinCleanBase, chineseCleanBase), preferChinese = false)
        }
    }

    private fun fuseMixedTexts(latinText: String, chineseText: String): String {
        val latinLines = latinText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val chineseLines = chineseText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val maxSize = maxOf(latinLines.size, chineseLines.size)

        return buildList {
            for (index in 0 until maxSize) {
                val latinLine = latinLines.getOrNull(index).orEmpty()
                val chineseLine = chineseLines.getOrNull(index).orEmpty()
                add(fuseLine(latinLine, chineseLine))
            }
        }.joinToString("\n").trim()
    }

    private fun fuseLine(latinLine: String, chineseLine: String): String {
        if (latinLine.isBlank()) return chineseLine
        if (chineseLine.isBlank()) return latinLine

        val chineseDominant = chineseCharRatio(chineseLine) >= 0.30
        val latinStructured = isStructuredLine(latinLine)
        val chineseStructured = isStructuredLine(chineseLine)

        if (chineseDominant && !latinStructured) {
            return chineseLine
        }
        if (latinStructured && !chineseStructured) {
            return latinLine
        }

        val modelRegex = Regex("[A-Za-z]{1,}[-_/]?[A-Za-z0-9]{2,}|\\d+[A-Za-z]+|[A-Za-z]+\\d+")
        var base = if (chineseLine.length >= latinLine.length) chineseLine else latinLine

        if (!chineseDominant) {
            val latinModelTokens = modelRegex.findAll(latinLine).map { it.value }.toList()
            val missingTokens = latinModelTokens.filter { token -> !base.contains(token) }
            if (missingTokens.isNotEmpty()) {
                base += " " + missingTokens.joinToString(" ")
            }
        }

        return base.trim()
    }

    private fun cleanFinalText(text: String, preferChinese: Boolean): String {
        val normalized = text.lines().map { normalizeSpacing(it) }.filter { it.isNotBlank() }
        val filtered = normalized.map { filterGarbageTokens(it, preferChinese) }.filter { it.isNotBlank() }
        val deduped = removeConsecutiveDuplicateLines(filtered)
        return mergeLikelyBrokenNaturalLanguageLines(deduped, preferChinese).joinToString("\n").trim()
    }

    private fun normalizeSpacing(line: String): String {
        return line.replace(Regex("[ \t]+"), " ").trim()
    }

    private fun filterGarbageTokens(line: String, preferChinese: Boolean): String {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size <= 1) {
            return if (isLikelyGarbageToken(line, preferChinese)) "" else line
        }

        return tokens.filterNot { isLikelyGarbageToken(it, preferChinese) }.joinToString(" ").trim()
    }

    private fun isLikelyGarbageToken(token: String, preferChinese: Boolean): Boolean {
        if (token.isBlank()) return true
        if (token.any { it.code in 0x4E00..0x9FFF }) return false
        if (isStructuredLine(token)) return false

        val pureUpper = token.all { it.isLetter() && it.isUpperCase() }
        val hasWeirdChar = token.any { !(it.isLetterOrDigit() || it in "-_/.,:;()[]") }
        val tooManyConsonants = token.length >= 5 && token.count { it.lowercaseChar() in "aeiou" } == 0

        return when {
            hasWeirdChar -> true
            preferChinese && pureUpper && token.length >= 5 -> true
            preferChinese && tooManyConsonants -> true
            else -> false
        }
    }

    private fun removeConsecutiveDuplicateLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val result = mutableListOf(lines.first())
        for (i in 1 until lines.size) {
            if (lines[i] != lines[i - 1]) {
                result += lines[i]
            }
        }
        return result
    }

    private fun mergeLikelyBrokenNaturalLanguageLines(lines: List<String>, preferChinese: Boolean): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)

            val shouldMerge = next != null &&
                current.length <= 6 &&
                next.length >= 3 &&
                !endsWithSentencePunctuation(current) &&
                !isStructuredLine(current) &&
                !isStructuredLine(next) &&
                !isNumericLine(current) &&
                !isNumericLine(next) &&
                (!preferChinese || (containsChinese(current) && containsChinese(next)))

            if (shouldMerge) {
                result += (current + next)
                i += 2
            } else {
                result += current
                i += 1
            }
        }
        return result
    }

    private fun endsWithSentencePunctuation(text: String): Boolean {
        return listOf("。", "；", ";", ":", "：", ".", "!", "?", "？").any { text.endsWith(it) }
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { it.code in 0x4E00..0x9FFF }
    }

    private fun isNumericLine(text: String): Boolean {
        return text.matches(Regex("^[0-9]+([.,][0-9]+)?$"))
    }

    private fun isStructuredLine(text: String): Boolean {
        val modelRegex = Regex("^[A-Za-z]{1,}[-_/]?[A-Za-z0-9]{2,}$|^[A-Za-z0-9-_/]{4,}$")
        val digitCount = text.count { it.isDigit() }
        val letterCount = text.count { it.isLetter() }
        val alphaNumHeavy = digitCount + letterCount >= text.length * 0.7
        return modelRegex.matches(text) || alphaNumHeavy
    }

    private fun chineseCharRatio(text: String): Double {
        if (text.isBlank()) return 0.0
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val chinese = chars.count { it.code in 0x4E00..0x9FFF }
        return chinese.toDouble() / chars.length.toDouble()
    }

    private fun latinUsefulRatio(text: String): Double {
        if (text.isBlank()) return 0.0
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val useful = chars.count { it.isLetterOrDigit() || it in "-_/.,:;()[]" }
        return useful.toDouble() / chars.length.toDouble()
    }

    private fun latinGarbageTokenRatio(text: String): Double {
        val tokens = text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 1.0
        val garbage = tokens.count { isLikelyGarbageToken(it, preferChinese = true) }
        return garbage.toDouble() / tokens.size.toDouble()
    }

    private fun copyToAppPrivateStorage(originalUri: String): String {
        val sourceUri = Uri.parse(originalUri)
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(sourceUri)
            ?: error("Unable to open input stream for uri: $originalUri")

        val documentsDir = File(context.filesDir, "documents")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val extension = resolver.getType(sourceUri)
            ?.substringAfterLast('/', "jpg")
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"

        val targetFile = File(documentsDir, "doc_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return Uri.fromFile(targetFile).toString()
    }

    private fun DocumentEntity.toModel(): Document {
        return Document(
            id = id,
            originalUri = originalUri,
            appUri = appUri,
            createdAt = createdAt,
            status = status,
            ocrStatus = ocrStatus,
            latinRawText = latinRawText,
            chineseRawText = chineseRawText,
            finalOcrText = finalOcrText,
            ocrRawText = ocrRawText,
            ocrUpdatedAt = ocrUpdatedAt
        )
    }
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class Duplicate(val existing: Document) : ImportResult
}

sealed interface OcrResult {
    data object Success : OcrResult
    data class Failed(val message: String) : OcrResult
    data object DocumentNotFound : OcrResult
}
