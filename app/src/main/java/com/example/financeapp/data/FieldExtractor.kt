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
        return ExtractedFields(
            counterpartyName = findCounterpartyForContract(text),
            documentDate = findDate(text),
            contractNo = findByLabels(text, listOf("合同编号", "合同号", "编号")),
            productName = findByLabels(text, listOf("产品名称", "货物名称", "品名")),
            productModel = findByLabels(text, listOf("产品型号", "规格型号", "型号")),
            quantity = findByLabels(text, listOf("数量", "采购数量", "销售数量")),
            unitPrice = findAmountByLabels(text, listOf("单价", "含税单价", "未税单价")),
            totalAmount = findAmountByLabels(text, listOf("总价", "合同金额", "合计", "金额"))
        )
    }

    private fun extractPaymentFields(text: String, docType: String): ExtractedFields {
        val direction = if (docType == DocTypes.RECEIPT) DocTypes.RECEIPT else DocTypes.PAYMENT
        return ExtractedFields(
            transactionDate = findDate(text),
            counterpartyName = findCounterpartyForTransfer(text, direction),
            amount = findAmountByLabels(text, listOf("交易金额", "金额", "转账金额", "付款金额", "收款金额")),
            direction = direction
        )
    }

    private fun findCounterpartyForContract(text: String): String? {
        val candidate = listOf(
            findByLabels(text, listOf("需方", "买方", "采购方", "甲方")),
            findByLabels(text, listOf("供方", "卖方", "供应商", "乙方"))
        ).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }

        return candidate ?: findCompanyLikeName(text)
    }

    private fun findCounterpartyForTransfer(text: String, direction: String): String? {
        val payee = findByLabels(text, listOf("收款人名称", "收款账户", "收款方", "收款人"))
        val payer = findByLabels(text, listOf("付款人名称", "付款账户", "付款方", "付款人"))

        return when (direction) {
            DocTypes.PAYMENT -> listOf(payee, payer).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
            else -> listOf(payer, payee).firstOrNull { !it.isNullOrBlank() && !it.contains(companyName) }
        }
    }

    private fun findDate(text: String): String? {
        val regexes = listOf(
            Regex("(20\\d{2}[年/\\-.]\\d{1,2}[月/\\-.]\\d{1,2}日?)"),
            Regex("(20\\d{2}\\d{2}\\d{2})")
        )
        return regexes.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
    }

    private fun findAmountByLabels(text: String, labels: List<String>): String? {
        val byLabel = findByLabels(text, labels)?.let { normalizeAmountToken(it) }
        if (!byLabel.isNullOrBlank()) return byLabel

        val fallback = Regex("(?:¥|￥)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        return fallback?.replace(",", "")
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
        val match = Regex("([\\u4e00-\\u9fa5A-Za-z0-9]{2,}(?:有限公司|集团|实业|科技|贸易|商行|厂))")
            .findAll(text)
            .map { it.value }
            .firstOrNull { !it.contains(companyName) }
        return match
    }
}

data class ExtractResult(
    val fields: ExtractedFields,
    val reason: String
)
