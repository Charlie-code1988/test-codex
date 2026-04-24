package com.example.financeapp.data

class OcrTextProcessor {

    fun buildFinalOcrText(latinText: String, chineseText: String): String {
        val latinClean = cleanText(latinText, preferChinese = false)
        val chineseClean = cleanText(chineseText, preferChinese = true)

        val latinScore = scoreText(latinClean, preferChinese = false)
        val chineseScore = scoreText(chineseClean, preferChinese = true)

        val preferChinese = chineseScore >= latinScore * 1.15 && chineseCharRatio(chineseClean) >= 0.20
        val preferLatin = latinScore >= chineseScore * 1.15

        return when {
            preferChinese -> chineseClean
            preferLatin -> latinClean
            else -> cleanText(fuseMixedTexts(latinClean, chineseClean), preferChinese = false)
        }
    }

    fun cleanText(text: String, preferChinese: Boolean): String {
        val lines = text.lines().map { normalizeSpacing(it) }.filter { it.isNotBlank() }
        val filtered = lines.map { filterGarbageTokens(it, preferChinese) }.filter { it.isNotBlank() }
        val deduped = removeConsecutiveDuplicateLines(filtered)
        val merged = mergeLikelyBrokenNaturalLanguageLines(deduped, preferChinese)
        return merged.joinToString("\n").trim()
    }

    private fun fuseMixedTexts(latinText: String, chineseText: String): String {
        val latinLines = latinText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val chineseLines = chineseText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val maxSize = maxOf(latinLines.size, chineseLines.size)

        return buildList {
            for (index in 0 until maxSize) {
                add(fuseLine(latinLines.getOrNull(index).orEmpty(), chineseLines.getOrNull(index).orEmpty()))
            }
        }.joinToString("\n").trim()
    }

    private fun fuseLine(latinLine: String, chineseLine: String): String {
        if (latinLine.isBlank()) return chineseLine
        if (chineseLine.isBlank()) return latinLine

        val chineseNatural = containsChinese(chineseLine) && !isStructuredLine(chineseLine)
        val latinStructured = isStructuredLine(latinLine)
        if (chineseNatural && !latinStructured) return chineseLine
        if (latinStructured && !isStructuredLine(chineseLine)) return latinLine

        val base = if (chineseLine.length >= latinLine.length) chineseLine else latinLine
        if (chineseNatural) return base

        val latinModelTokens = MODEL_REGEX.findAll(latinLine).map { it.value }.toList()
        val missing = latinModelTokens.filter { !base.contains(it) && !isLikelyGarbageToken(it, preferChinese = false) }
        return (base + if (missing.isNotEmpty()) " ${missing.joinToString(" ")}" else "").trim()
    }

    private fun scoreText(text: String, preferChinese: Boolean): Double {
        if (text.isBlank()) return 0.0
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0

        val chineseRatio = chineseCharRatio(text)
        val structuredLines = text.lines().count { isStructuredLine(it.trim()) }
        val lineCount = text.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        val garbageRatio = garbageTokenRatio(text, preferChinese)

        return if (preferChinese) {
            chineseRatio * 0.65 + (1.0 - garbageRatio) * 0.30 + (structuredLines.toDouble() / lineCount) * 0.05
        } else {
            latinUsefulRatio(text) * 0.65 + (1.0 - garbageRatio) * 0.30 + (structuredLines.toDouble() / lineCount) * 0.05
        }
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
        if (containsChinese(token)) return false
        if (isStructuredLine(token)) return false

        val hasWeirdChar = token.any { !(it.isLetterOrDigit() || it in "-_/.,:;()[]") }
        val pureUpperLong = token.all { it.isLetter() && it.isUpperCase() } && token.length >= 5
        val noVowelLong = token.length >= 5 && token.count { it.lowercaseChar() in "aeiou" } == 0

        return when {
            hasWeirdChar -> true
            preferChinese && pureUpperLong -> true
            preferChinese && noVowelLong -> true
            else -> false
        }
    }

    private fun removeConsecutiveDuplicateLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val out = mutableListOf(lines.first())
        for (i in 1 until lines.size) {
            if (lines[i] != lines[i - 1]) out += lines[i]
        }
        return out
    }

    private fun mergeLikelyBrokenNaturalLanguageLines(lines: List<String>, preferChinese: Boolean): List<String> {
        if (looksLikeTable(lines)) return lines

        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)

            val shouldMerge = next != null &&
                current.length <= 6 &&
                next.length >= 3 &&
                !endsWithSentencePunctuation(current) &&
                !isNumericLine(current) &&
                !isNumericLine(next) &&
                !isStructuredLine(current) &&
                !isStructuredLine(next) &&
                (!preferChinese || (containsChinese(current) && containsChinese(next)))

            if (shouldMerge) {
                result += current + next
                i += 2
            } else {
                result += current
                i += 1
            }
        }
        return result
    }

    private fun looksLikeTable(lines: List<String>): Boolean {
        val modelLines = lines.count { isStructuredLine(it) }
        val numericLines = lines.count { isNumericLine(it) }
        return modelLines >= 2 && numericLines >= 2
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
        if (text.isBlank()) return false
        val t = text.trim()
        if (MODEL_REGEX.matches(t)) return true
        val letters = t.count { it.isLetter() }
        val digits = t.count { it.isDigit() }
        return letters + digits >= t.length * 0.7 && (letters > 0 && digits > 0)
    }

    private fun chineseCharRatio(text: String): Double {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val chineseCount = chars.count { it.code in 0x4E00..0x9FFF }
        return chineseCount.toDouble() / chars.length.toDouble()
    }

    private fun latinUsefulRatio(text: String): Double {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 0.0
        val useful = chars.count { it.isLetterOrDigit() || it in "-_/.,:;()[]" }
        return useful.toDouble() / chars.length.toDouble()
    }

    private fun garbageTokenRatio(text: String, preferChinese: Boolean): Double {
        val tokens = text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 1.0
        val garbage = tokens.count { isLikelyGarbageToken(it, preferChinese) }
        return garbage.toDouble() / tokens.size.toDouble()
    }

    companion object {
        private val MODEL_REGEX = Regex("^[A-Za-z]{1,}[-_/]?[A-Za-z0-9]{2,}$|^[A-Za-z0-9-_/]{4,}$")
    }
}
