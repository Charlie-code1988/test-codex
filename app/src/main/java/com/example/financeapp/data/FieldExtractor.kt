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

        val fields = when (docType) {
            DocTypes.SALES_CONTRACT, DocTypes.PURCHASE_CONTRACT -> extractContractFields(text)
            DocTypes.RECEIPT, DocTypes.PAYMENT -> extractTransferFields(text, docType)
            else -> ExtractedFields()
        }

        return ExtractResult(fields, "规则提取完成：docType=$docType")
    }

    private fun extractContractFields(text: String): ExtractedFields {
        val lineItems = parseContractLineItems(text)
        val first = lineItems.firstOrNull()

        val total = findAmountNearLabels(text, listOf("合计", "总金额", "合同金额", "总价", "人民币大写", "小计"))
            ?: if (lineItems.isNotEmpty()) sumLineTotals(lineItems) else null

        return ExtractedFields(
            counterpartyName = findCounterpartyForContract(text),
            documentDate = findDateByLabels(text, listOf("签订日期", "合同日期", "日期")) ?: findDate(text),
            contractNo = findByLabels(text, listOf("合同编号", "合同号")),
            productName = first?.productName,
            productModel = first?.productModel,
            quantity = first?.quantity,
            unitPrice = first?.unitPrice,
            totalAmount = total,
            lineItems = lineItems
        )
    }

    private fun extractTransferFields(text: String, docType: String): ExtractedFields {
        val direction = if (docType == DocTypes.RECEIPT) DocTypes.RECEIPT else DocTypes.PAYMENT

        return ExtractedFields(
            transactionDate = findDateByLabels(text, listOf("交易时间", "交易日期", "转账时间", "支付时间", "日期")) ?: findDate(text),
            counterpartyName = findCounterpartyForTransfer(text, direction),
            amount = findAmountNearLabels(text, listOf("转账金额", "交易金额", "收款金额", "付款金额", "小写", "人民币金额", "金额", "￥", "¥")),
            direction = direction
        )
    }

    private fun parseContractLineItems(text: String): List<ExtractedLineItem> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { isHeaderLine(it) }
        if (headerIndex < 0) return emptyList()

        val result = mutableListOf<ExtractedLineItem>()
        for (idx in (headerIndex + 1) until lines.size) {
            val line = lines[idx]
            if (isSummaryLine(line)) break
            if (isHeaderLine(line)) continue

            parseLineItem(line)?.let { result += it }
        }
        return result
    }

    private fun isHeaderLine(line: String): Boolean {
        val keywords = listOf("产品名称", "名称", "品名", "型号", "规格", "规格型号", "规格/型号", "数量", "单价", "含税单价", "小计", "金额", "总金额")
        val hitCount = keywords.count { line.contains(it) }
        return hitCount >= 3
    }

    private fun isSummaryLine(line: String): Boolean {
        return line.contains("合计") || line.contains("总金额") || line.contains("价税合计") || line.contains("人民币")
    }

    private fun parseLineItem(line: String): ExtractedLineItem? {
        val tokens = line.split(Regex("\\s+|[|｜]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val numeric = tokens.mapIndexedNotNull { i, token ->
            if (isNumericForTable(token)) i to normalizeAmount(token) else null
        }
        if (numeric.isEmpty()) return null

        val firstNumericIndex = numeric.first().first
        if (firstNumericIndex == 0) return null

        val productTokens = tokens.subList(0, firstNumericIndex)
        if (productTokens.isEmpty()) return null
        val productName = productTokens.first()
        val productModel = productTokens.drop(1).joinToString(" ")

        val values = numeric.map { it.second }
        if (values.isEmpty()) return null

        val quantityCandidate = values.firstOrNull { looksLikeQuantity(it) }
        val unitPriceCandidate = values.firstOrNull { looksLikeMoney(it) && it != quantityCandidate }
        val lineTotalCandidate = values.lastOrNull { looksLikeMoney(it) }

        if (quantityCandidate == null && unitPriceCandidate == null && lineTotalCandidate == null) return null

        val lineTotal = lineTotalCandidate ?: calculateLineTotal(quantityCandidate, unitPriceCandidate)

        return ExtractedLineItem(
            productName = productName,
            productModel = productModel,
            quantity = quantityCandidate ?: "",
            unitPrice = unitPriceCandidate ?: "",
            lineTotal = lineTotal ?: ""
        )
    }

    private fun isNumericForTable(token: String): Boolean {
        if (token.contains("RMB", ignoreCase = true)) return false
        if (token.matches(Regex("20\\d{2}[/-]\\d{1,2}[/-]\\d{1,2}"))) return false
        if (token.length >= 11 && !token.contains(".")) return false
        val normalized = normalizeAmount(token)
        return normalized.matches(Regex("\\d+(?:\\.\\d{1,2})?"))
    }

    private fun looksLikeQuantity(value: String): Boolean {
        val d = value.toDoubleOrNull() ?: return false
        if (d <= 0.0 || d > 100000) return false
        return d <= 10000
    }

    private fun looksLikeMoney(value: String): Boolean {
        val d = value.toDoubleOrNull() ?: return false
        return d > 0 && d < 100000000
    }

    private fun calculateLineTotal(quantity: String?, unitPrice: String?): String? {
        val q = quantity?.toDoubleOrNull() ?: return null
        val p = unitPrice?.toDoubleOrNull() ?: return null
        val calc = (q * p * 100).roundToLong() / 100.0
        return if (calc % 1.0 == 0.0) calc.toInt().toString() else String.format("%.2f", calc)
    }

    private fun sumLineTotals(items: List<ExtractedLineItem>): String? {
        val totals = items.mapNotNull { it.lineTotal.toDoubleOrNull() }
        if (totals.isEmpty()) return null
        val sum = totals.sum()
        return String.format("%.2f", sum)
    }

    private fun findCounterpartyForContract(text: String): String? {
        val fromRole = listOf(
            findByLabels(text, listOf("需方", "买方", "采购方", "甲方")),
            findByLabels(text, listOf("供方", "卖方", "供应商", "乙方"))
        ).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }

        return fromRole ?: findCompanyLikeName(text)
    }

    private fun findCounterpartyForTransfer(text: String, direction: String): String? {
        val payee = findByLabels(text, listOf("收款户名", "收款账户户名", "收款人", "收款方", "收款账户"))
        val payer = findByLabels(text, listOf("付款户名", "付款账户户名", "付款人", "付款方", "付款账户"))

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
            Regex("(\\d{1,2}月\\d{1,2}日)"),
            Regex("(20\\d{2}/\\d{1,2}/\\d{1,2})")
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
    }

    private fun findAmountNearLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 80))
            val amount = findBestAmount(window)
            if (!amount.isNullOrBlank()) return amount
        }
        return null
    }

    private fun findBestAmount(text: String): String? {
        val regex = Regex("(?:¥|￥)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+\\.[0-9]{1,2}|[0-9]{1,6})")
        return regex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { normalizeAmount(it) }
            .firstOrNull { isValidAmount(it) }
    }

    private fun isValidAmount(value: String): Boolean {
        if (!value.matches(Regex("\\d+(?:\\.\\d{1,2})?"))) return false
        if (value.length >= 11 && !value.contains('.')) return false
        if (value.length == 11 && !value.contains('.')) return false
        val d = value.toDoubleOrNull() ?: return false
        return d > 0
    }

    private fun normalizeAmount(raw: String): String {
        return raw.replace("¥", "").replace("￥", "").replace(",", "").trim()
    }

    private fun findByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx + label.length, minOf(text.length, idx + label.length + 64))
            val value = window
                .trimStart('：', ':', ' ', '\t')
                .takeWhile { it !in listOf('\n', '，', ',', ';', '；') }
                .trim()
            if (value.isNotBlank() && !isNoiseLabelValue(value)) return value
        }
        return null
    }

    private fun isNoiseLabelValue(value: String): Boolean {
        val noiseWords = listOf("账号", "税号", "统一社会信用代码", "合同号", "流水号", "电话", "手机", "地址")
        if (noiseWords.any { value.contains(it) }) return true
        if (value.matches(Regex("\\d{11,}"))) return true
        return false
    }

    private fun findCompanyLikeName(text: String): String? {
        val regex = Regex("([\\u4e00-\\u9fa5A-Za-z0-9（）()]{2,}(?:有限公司|公司|厂|个体工商户))")
        return regex.findAll(text)
            .map { it.value }
            .firstOrNull { !it.contains(companyName) }
    }
}

data class ExtractResult(
    val fields: ExtractedFields,
    val reason: String
)
