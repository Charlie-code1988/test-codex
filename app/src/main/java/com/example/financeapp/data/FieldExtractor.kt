package com.example.financeapp.data

import kotlin.math.roundToLong

class FieldExtractor(
    private val companyName: String = "浙江拜伦智能科技有限公司"
) {

    fun extract(docType: String, finalOcrText: String): ExtractResult {
        val text = finalOcrText.trim()
        if (text.isBlank()) {
            return ExtractResult(ExtractedFields(), "OCR 文本为空")
        }

        val debug = mutableListOf<String>()
        debug += "docType=$docType"

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        debug += "ocrLines=${lines.size}"
        lines.forEachIndexed { i, line -> debug += "[$i] $line" }

        val fields = when (docType) {
            DocTypes.SALES_CONTRACT, DocTypes.PURCHASE_CONTRACT -> extractContractFields(text, lines, debug)
            DocTypes.RECEIPT, DocTypes.PAYMENT -> extractTransferFields(text, docType, debug)
            else -> ExtractedFields()
        }

        debug += "finalLineItems=${fields.lineItems.size}"
        return ExtractResult(fields, debug.joinToString("\n"))
    }

    private fun extractContractFields(text: String, lines: List<String>, debug: MutableList<String>): ExtractedFields {
        val lineItems = parseContractLineItems(lines, debug)
        val first = lineItems.firstOrNull()

        val totalAmount = findReliableTotalAmount(text)
            ?: sumLineTotals(lineItems)

        return ExtractedFields(
            counterpartyName = findCounterpartyForContract(text),
            documentDate = findDateByLabels(text, listOf("签订日期", "合同日期", "日期")) ?: findDate(text),
            contractNo = findByLabels(text, listOf("合同编号", "合同号")),
            productName = first?.productName,
            productModel = first?.productModel,
            quantity = first?.quantity,
            unitPrice = first?.unitPrice,
            totalAmount = totalAmount,
            lineItems = lineItems
        )
    }

    private fun extractTransferFields(text: String, docType: String, debug: MutableList<String>): ExtractedFields {
        val direction = if (docType == DocTypes.RECEIPT) DocTypes.RECEIPT else DocTypes.PAYMENT
        val txDate = findDateByLabels(text, listOf("交易时间", "交易日期", "转账时间", "支付时间", "日期")) ?: findDate(text)
        val counterparty = findCounterpartyForTransfer(text, direction)
        val amount = findAmountNearLabels(text, listOf("转账金额", "交易金额", "收款金额", "付款金额", "金额", "小写", "￥", "¥"))

        debug += "transferCounterparty=$counterparty"
        debug += "transferDate=$txDate"
        debug += "transferAmount=$amount"

        return ExtractedFields(
            transactionDate = txDate,
            counterpartyName = counterparty,
            amount = amount,
            direction = direction
        )
    }

    private fun parseContractLineItems(lines: List<String>, debug: MutableList<String>): List<ExtractedLineItem> {
        val headerIndex = lines.indexOfFirst { isHeaderLine(it) }
        debug += "headerDetected=${headerIndex >= 0}"
        debug += "headerIndex=$headerIndex"
        if (headerIndex >= 0) debug += "headerLine=${lines[headerIndex]}"

        val startIndex = if (headerIndex >= 0) headerIndex + 1 else 0

        val items = mutableListOf<ExtractedLineItem>()
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            if (isHardStopSummaryLine(line)) {
                debug += "stopLine[$i]=$line"
                break
            }
            if (isHeaderLine(line)) {
                debug += "reject(header)[$i]=$line"
                i++
                continue
            }
            if (isIgnoredRow(line)) {
                debug += "reject(ignored)[$i]=$line"
                i++
                continue
            }

            val single = parseContractDataLine(line)
            if (single != null) {
                debug += "accept(single)[$i]=${single.productName}|${single.productModel}|${single.quantity}|${single.unitPrice}|${single.lineTotal}"
                items += single
                i++
                continue
            }

            var accepted = false
            for (groupSize in 2..3) {
                if (i + groupSize - 1 >= lines.size) continue
                val merged = (0 until groupSize).joinToString(" ") { offset -> lines[i + offset] }
                if ((1 until groupSize).any { isIgnoredRow(lines[i + it]) || isHardStopSummaryLine(lines[i + it]) }) continue

                debug += "candidate(merged${groupSize})[$i]=${merged}"
                val mergedItem = parseContractDataLine(merged)
                if (mergedItem != null) {
                    debug += "accept(merged${groupSize})[$i]=${mergedItem.productName}|${mergedItem.productModel}|${mergedItem.quantity}|${mergedItem.unitPrice}|${mergedItem.lineTotal}"
                    items += mergedItem
                    i += groupSize
                    accepted = true
                    break
                }
            }

            if (!accepted) {
                debug += "reject(parse_failed)[$i]=$line"
                i++
            }
        }

        debug += "lineItemsGenerated=${items.size}"
        return items
    }

    private fun isHeaderLine(line: String): Boolean {
        val keys = listOf("产品名称", "名称", "品名", "型号", "规格", "规格型号", "规格/型号", "数量", "单价", "含税单价", "小计", "金额", "总金额")
        return keys.count { line.contains(it) } >= 2
    }

    private fun isHardStopSummaryLine(line: String): Boolean {
        return line.contains("合计") || line.contains("合计大写") || line.contains("人民币大写") || line.contains("价税合计")
    }

    private fun isIgnoredRow(line: String): Boolean {
        val noise = listOf("备注", "税号", "账号", "银行账号", "电话", "手机号", "地址", "日期", "合同编号", "流水号")
        return noise.any { line.contains(it) }
    }

    private fun parseContractDataLine(line: String): ExtractedLineItem? {
        val tokens = line.split(Regex("\\s+|[|｜]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.size < 3) return null

        val numericIdx = tokens.mapIndexedNotNull { idx, token -> if (isNumericToken(token)) idx else null }
        if (numericIdx.size < 2) return null

        val firstNumeric = numericIdx.first()
        if (firstNumeric <= 0) return null

        val productTokens = tokens.subList(0, firstNumeric)
        val productName = productTokens.firstOrNull().orEmpty()
        val productModel = productTokens.drop(1).joinToString(" ")

        val hasChineseProduct = containsChinese(productName)
        val hasModelLike = Regex("[A-Za-z]+[0-9-]{1,}").containsMatchIn(productModel.ifBlank { line })
        if (!hasChineseProduct && !hasModelLike) return null

        val nums = numericIdx.map { normalizeAmount(tokens[it]) }
        if (nums.size < 2) return null

        val quantity = nums[0]
        val unitPrice = nums[1]
        val lineTotal = nums.getOrNull(2) ?: calcLineTotal(quantity, unitPrice)

        if (!looksLikeQuantity(quantity) || !looksLikePrice(unitPrice)) return null

        return ExtractedLineItem(
            productName = productName,
            productModel = productModel,
            quantity = quantity,
            unitPrice = unitPrice,
            lineTotal = lineTotal
        )
    }

    private fun findReliableTotalAmount(text: String): String? {
        val labels = listOf("合计", "总金额", "合同金额", "总价")
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 80))
            val amount = findBestAmount(window, allowSmallInteger = false)
            if (!amount.isNullOrBlank()) return amount
        }
        return null
    }

    private fun sumLineTotals(items: List<ExtractedLineItem>): String? {
        val sum = items.mapNotNull { it.lineTotal.toDoubleOrNull() }.sum()
        if (sum <= 0) return null
        return if (sum % 1.0 == 0.0) sum.toInt().toString() else String.format("%.2f", sum)
    }

    private fun findCounterpartyForContract(text: String): String? {
        val candidate = listOf(
            findByLabels(text, listOf("需方", "买方", "采购方", "甲方")),
            findByLabels(text, listOf("供方", "卖方", "供应商", "乙方"))
        ).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
        return candidate ?: findCompanyLikeName(text)
    }

    private fun findCounterpartyForTransfer(text: String, direction: String): String? {
        val payee = findByLabels(text, listOf("收款账户户名", "收款户名", "收款人", "收款方", "收款账户"))
        val payer = findByLabels(text, listOf("付款账户户名", "付款户名", "付款人", "付款方", "付款账户"))
        val ordered = if (direction == DocTypes.PAYMENT) listOf(payee, payer) else listOf(payer, payee)
        return ordered.firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
            ?: ordered.firstOrNull { !it.isNullOrBlank() }
    }

    private fun findDateByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 80))
            val date = findDate(window)
            if (!date.isNullOrBlank()) return date
        }
        return null
    }

    private fun findDate(text: String): String? {
        val patterns = listOf(
            Regex("(20\\d{2}[年/\\-.]\\d{1,2}[月/\\-.]\\d{1,2}(?:日)?(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)"),
            Regex("(20\\d{2}/\\d{1,2}/\\d{1,2})"),
            Regex("(\\d{1,2}月\\d{1,2}日)")
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
    }

    private fun findAmountNearLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 80))
            val amount = findBestAmount(window, allowSmallInteger = true)
            if (!amount.isNullOrBlank()) return amount
        }
        return null
    }

    private fun findBestAmount(window: String, allowSmallInteger: Boolean): String? {
        val regex = Regex("(?:¥|￥)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+(?:\\.[0-9]{1,2})?)")
        return regex.findAll(window)
            .mapNotNull { m ->
                val raw = m.groupValues.getOrNull(1) ?: return@mapNotNull null
                val normalized = normalizeAmount(raw)
                if (isReliableAmount(normalized, raw, allowSmallInteger)) normalized else null
            }
            .firstOrNull()
    }

    private fun isReliableAmount(value: String, raw: String, allowSmallInteger: Boolean): Boolean {
        if (!value.matches(Regex("\\d+(?:\\.\\d{1,2})?"))) return false
        if (value.length >= 11 && !value.contains('.')) return false
        val number = value.toDoubleOrNull() ?: return false
        if (number <= 0) return false
        val hasCurrencyStyle = raw.contains(".") || raw.contains(",") || raw.contains("¥") || raw.contains("￥")
        if (!hasCurrencyStyle && !allowSmallInteger && number < 100) return false
        if (!hasCurrencyStyle && value.length > 6) return false
        return true
    }

    private fun findByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx + label.length, minOf(text.length, idx + label.length + 64))
            val value = window.trimStart('：', ':', ' ', '\t').takeWhile { it !in listOf('\n', '，', ',', ';', '；') }.trim()
            if (value.isNotBlank() && !isNoiseValue(value)) return value
        }
        return null
    }

    private fun isNoiseValue(value: String): Boolean {
        val noise = listOf("账号", "税号", "统一社会信用代码", "电话", "手机", "地址", "合同编号", "流水号")
        if (noise.any { value.contains(it) }) return true
        if (value.matches(Regex("\\d{11,}"))) return true
        return false
    }

    private fun findCompanyLikeName(text: String): String? {
        val regex = Regex("([\\u4e00-\\u9fa5A-Za-z0-9（）()]{2,}(?:有限公司|公司|厂|个体工商户))")
        return regex.findAll(text).map { it.value }.firstOrNull { !it.contains(companyName) }
    }

    private fun isNumericToken(token: String): Boolean {
        if (token.contains("RMB", ignoreCase = true)) return false
        if (token.matches(Regex("20\\d{2}[/-]\\d{1,2}[/-]\\d{1,2}"))) return false
        if (token.length >= 11 && !token.contains('.')) return false
        val value = normalizeAmount(token)
        return value.matches(Regex("\\d+(?:\\.\\d{1,2})?"))
    }

    private fun containsChinese(text: String): Boolean = text.any { it.code in 0x4E00..0x9FFF }

    private fun looksLikeQuantity(value: String): Boolean {
        val n = value.toDoubleOrNull() ?: return false
        return n > 0 && n <= 10000
    }

    private fun looksLikePrice(value: String): Boolean {
        val n = value.toDoubleOrNull() ?: return false
        return n > 0 && n <= 10000000
    }

    private fun calcLineTotal(quantity: String, unitPrice: String): String {
        val q = quantity.toDoubleOrNull() ?: return ""
        val p = unitPrice.toDoubleOrNull() ?: return ""
        val total = (q * p * 100).roundToLong() / 100.0
        return if (total % 1.0 == 0.0) total.toInt().toString() else String.format("%.2f", total)
    }

    private fun normalizeAmount(raw: String): String {
        return raw.replace("¥", "").replace("￥", "").replace(",", "").trim()
    }
}

data class ExtractResult(
    val fields: ExtractedFields,
    val reason: String
)
