package com.example.financeapp.data

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
            DocTypes.RECEIPT, DocTypes.PAYMENT -> extractPaymentFields(text, docType)
            else -> ExtractedFields()
        }

        return ExtractResult(fields, "规则提取完成：docType=$docType")
    }

    private fun extractContractFields(text: String): ExtractedFields {
        val tableRow = parseFirstContractTableRow(text)

        return ExtractedFields(
            counterpartyName = findCounterpartyForContract(text),
            documentDate = findDate(text),
            contractNo = findByLabels(text, listOf("合同编号", "合同号")),
            productName = tableRow?.productName ?: findByLabels(text, listOf("产品名称", "货物名称", "品名", "名称")),
            productModel = tableRow?.productModel ?: findByLabels(text, listOf("产品型号", "规格型号", "规格/型号", "型号")),
            quantity = tableRow?.quantity ?: findByLabels(text, listOf("数量", "采购数量", "销售数量")),
            unitPrice = tableRow?.unitPrice ?: findAmountByLabels(text, listOf("单价", "含税单价", "未税单价")),
            totalAmount = findAmountByLabels(text, listOf("合计", "总金额", "合同金额", "总价", "小计"))
                ?: tableRow?.lineTotal
        )
    }

    private fun extractPaymentFields(text: String, docType: String): ExtractedFields {
        val direction = if (docType == DocTypes.RECEIPT) DocTypes.RECEIPT else DocTypes.PAYMENT
        return ExtractedFields(
            transactionDate = findDateByLabels(text, listOf("交易时间", "交易日期", "日期")) ?: findDate(text),
            counterpartyName = findCounterpartyForTransfer(text, direction),
            amount = findAmountByLabels(text, listOf("转账金额", "交易金额", "小写金额", "人民币金额", "金额")),
            direction = direction
        )
    }

    private fun parseFirstContractTableRow(text: String): ContractLineData? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headerKeywords = listOf("产品名称", "名称", "型号", "规格/型号", "规格型号", "数量", "单价", "含税单价", "小计", "总金额", "合计")
        val headerIndex = lines.indexOfFirst { line ->
            val matchedHeaderCount = headerKeywords.count { line.contains(it) }
            matchedHeaderCount >= 3
        }
        if (headerIndex < 0) return null

        for (index in (headerIndex + 1) until lines.size) {
            val line = lines[index]
            if (line.contains("合计") || line.contains("总金额")) continue
            if (line.contains("RMB", ignoreCase = true) || line.contains("含税单价")) continue

            val parsed = parseContractDataLine(line)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseContractDataLine(line: String): ContractLineData? {
        val tokens = line.split(Regex("\\s+|[|｜]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.size < 4) return null

        val numericIndices = tokens.mapIndexedNotNull { idx, token -> if (token.isPossibleNumericToken()) idx else null }
        if (numericIndices.size < 3) return null

        val lineTotalToken = tokens[numericIndices.last()]
        val unitPriceToken = tokens[numericIndices[numericIndices.size - 2]]
        val quantityToken = tokens[numericIndices[numericIndices.size - 3]]

        val firstNumericIndex = numericIndices.first()
        if (firstNumericIndex <= 0) return null

        val productTokens = tokens.subList(0, firstNumericIndex)
        if (productTokens.isEmpty()) return null

        val productName = productTokens.first()
        val productModel = productTokens.drop(1).joinToString(" ").ifBlank { null }

        return ContractLineData(
            productName = productName,
            productModel = productModel,
            quantity = normalizeQuantity(quantityToken),
            unitPrice = normalizeAmountToken(unitPriceToken),
            lineTotal = normalizeAmountToken(lineTotalToken)
        )
    }

    private fun normalizeQuantity(raw: String): String {
        return raw.replace(",", "").trim()
    }

    private fun String.isPossibleNumericToken(): Boolean {
        val cleaned = this.replace("¥", "").replace("￥", "").replace(",", "").trim()
        if (cleaned.isBlank()) return false
        if (!Regex("\\d+(?:\\.\\d+)?").matches(cleaned)) return false
        return true
    }

    private fun findCounterpartyForContract(text: String): String? {
        val candidate = listOf(
            findByLabels(text, listOf("需方", "买方", "采购方", "甲方")),
            findByLabels(text, listOf("供方", "卖方", "供应商", "乙方"))
        ).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }

        return candidate ?: findCompanyLikeName(text)
    }

    private fun findCounterpartyForTransfer(text: String, direction: String): String? {
        val payee = findByLabels(text, listOf("收款户名", "收款人名称", "收款方", "收款人", "收款账户"))
        val payer = findByLabels(text, listOf("付款户名", "付款人名称", "付款方", "付款人", "付款账户"))

        return when (direction) {
            DocTypes.RECEIPT -> listOf(payer, payee).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
            else -> listOf(payee, payer).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
        }
    }

    private fun findDateByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 48))
            val dateTime = Regex("(20\\d{2}[年/\\-.]\\d{1,2}[月/\\-.]\\d{1,2}(?:日)?(?:\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?)")
                .find(window)
                ?.groupValues
                ?.getOrNull(1)
            if (!dateTime.isNullOrBlank()) return dateTime.trim()
        }
        return null
    }

    private fun findDate(text: String): String? {
        val regexes = listOf(
            Regex("(20\\d{2}[年/\\-.]\\d{1,2}[月/\\-.]\\d{1,2}日?(?:\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?)"),
            Regex("(20\\d{2}\\d{2}\\d{2})")
        )
        return regexes.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
    }

    private fun findAmountByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val idx = text.indexOf(label)
            if (idx < 0) return@forEach
            val window = text.substring(idx, minOf(text.length, idx + 60))
            val amount = findBestAmountCandidate(window)
            if (!amount.isNullOrBlank()) return amount
        }
        return null
    }

    private fun findBestAmountCandidate(text: String): String? {
        val regex = Regex("(?:¥|￥)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+\\.[0-9]{1,2}|[0-9]{1,6})")
        val candidates = regex.findAll(text)
            .mapNotNull { match ->
                val raw = match.groupValues.getOrNull(1) ?: return@mapNotNull null
                val normalized = normalizeAmountToken(raw)
                if (!isValidAmount(normalized, raw)) return@mapNotNull null
                normalized
            }
            .toList()

        return candidates.firstOrNull()
    }

    private fun isValidAmount(normalized: String, raw: String): Boolean {
        if (normalized.isBlank()) return false
        if (!Regex("\\d+(?:\\.\\d{1,2})?").matches(normalized)) return false
        if (normalized.length >= 10 && !normalized.contains('.')) return false

        val asNumber = normalized.toDoubleOrNull() ?: return false
        if (asNumber <= 0) return false

        val hasMoneyStyle = raw.contains(".") || raw.contains(",") || raw.contains("¥") || raw.contains("￥")
        if (!hasMoneyStyle && normalized.length > 6) return false

        val looksLikePhone = normalized.length == 11 && !normalized.contains('.')
        if (looksLikePhone) return false

        return true
    }

    private fun normalizeAmountToken(raw: String): String {
        return raw
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .trim()
    }

    private fun findByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val value = findAfterLabel(text, label)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun findAfterLabel(text: String, label: String): String? {
        val idx = text.indexOf(label)
        if (idx < 0) return null

        val start = idx + label.length
        val window = text.substring(start, minOf(text.length, start + 48))
        val cleaned = window
            .trimStart('：', ':', ' ', '\t')
            .takeWhile { it !in listOf('\n', '，', ',', '。', ';', '；') }
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun findCompanyLikeName(text: String): String? {
        return Regex("([\\u4e00-\\u9fa5A-Za-z0-9]{2,}(?:有限公司|集团|实业|科技|贸易|商行|厂))")
            .findAll(text)
            .map { it.value }
            .firstOrNull { !it.contains(companyName) }
    }
}

private data class ContractLineData(
    val productName: String,
    val productModel: String?,
    val quantity: String,
    val unitPrice: String,
    val lineTotal: String
)

data class ExtractResult(
    val fields: ExtractedFields,
    val reason: String
)
