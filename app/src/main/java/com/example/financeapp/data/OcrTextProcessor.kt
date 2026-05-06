package com.example.financeapp.data

class OcrTextProcessor {

    fun buildFinalOcrText(latinText: String, chineseText: String): String {
        val latinClean = cleanText(latinText)
        val chineseClean = cleanText(chineseText)

        if (latinClean.isBlank()) return chineseClean
        if (chineseClean.isBlank()) return latinClean

        val chineseRatio = chineseCharRatio(chineseClean)
        val latinRatio = latinAlphaNumRatio(latinClean)

        val preferChineseByLanguage = chineseRatio >= 0.25
        val preferLatinByLanguage = latinRatio >= 0.55 && chineseRatio < 0.15

        val preferChineseByCompleteness = chineseClean.length >= latinClean.length * 1.15
        val preferLatinByCompleteness = latinClean.length >= chineseClean.length * 1.15

        return when {
            preferChineseByLanguage -> chineseClean
            preferLatinByLanguage -> latinClean
            preferChineseByCompleteness -> chineseClean
            preferLatinByCompleteness -> latinClean
            else -> if (chineseRatio >= 0.20) chineseClean else latinClean
        }
    }

    fun cleanText(text: String): String {
        val normalizedLines = text
            .lines()
            .map { line ->
                line.trim().replace(Regex("[ \t]+"), " ")
            }
            .filter { it.isNotBlank() }

        if (normalizedLines.isEmpty()) return ""

        val deduped = mutableListOf(normalizedLines.first())
        for (i in 1 until normalizedLines.size) {
            if (normalizedLines[i] != normalizedLines[i - 1]) {
                deduped += normalizedLines[i]
            }
        }

        return deduped.joinToString("\n")
    }

    private fun chineseCharRatio(text: String): Double {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val chineseCount = chars.count { it.code in 0x4E00..0x9FFF }
        return chineseCount.toDouble() / chars.length.toDouble()
    }

    private fun latinAlphaNumRatio(text: String): Double {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val alphaNumCount = chars.count { it.isLetterOrDigit() || it in "-_/.,:;()[]" }
        return alphaNumCount.toDouble() / chars.length.toDouble()
    }
}
