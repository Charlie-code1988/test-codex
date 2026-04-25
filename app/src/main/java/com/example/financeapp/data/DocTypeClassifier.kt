package com.example.financeapp.data

data class ClassifyResult(
    val docType: String,
    val reason: String
)

class DocTypeClassifier {

    fun classify(text: String): ClassifyResult {
        val normalized = text.lowercase()
        if (normalized.isBlank()) {
            return ClassifyResult(DocTypes.UNKNOWN, "文本为空")
        }

        val salesKeywords = listOf("销售合同", "销售方", "客户", "应收")
        val purchaseKeywords = listOf("采购合同", "供应商", "应付", "采购方")
        val contractKeywords = listOf("销售合同", "购销合同", "甲方", "乙方", "合同编号", "供方", "需方")
        val receiptKeywords = listOf("收款", "收据", "已收", "回款", "银行回单收入")
        val paymentKeywords = listOf("付款", "支付", "已付", "银行回单支出")

        val salesMatched = salesKeywords.filter { normalized.contains(it) }
        val purchaseMatched = purchaseKeywords.filter { normalized.contains(it) }
        val contractMatched = contractKeywords.filter { normalized.contains(it) }
        val receiptMatched = receiptKeywords.filter { normalized.contains(it) }
        val paymentMatched = paymentKeywords.filter { normalized.contains(it) }

        if (receiptMatched.isNotEmpty() && receiptMatched.size >= paymentMatched.size) {
            return ClassifyResult(DocTypes.RECEIPT, "匹配收款关键词: ${receiptMatched.joinToString(",")}")
        }
        if (paymentMatched.isNotEmpty() && paymentMatched.size > receiptMatched.size) {
            return ClassifyResult(DocTypes.PAYMENT, "匹配付款关键词: ${paymentMatched.joinToString(",")}")
        }

        if (salesMatched.isNotEmpty() && salesMatched.size >= purchaseMatched.size) {
            return ClassifyResult(DocTypes.SALES_CONTRACT, "匹配销售关键词: ${salesMatched.joinToString(",")}")
        }
        if (purchaseMatched.isNotEmpty() && purchaseMatched.size > salesMatched.size) {
            return ClassifyResult(DocTypes.PURCHASE_CONTRACT, "匹配采购关键词: ${purchaseMatched.joinToString(",")}")
        }

        if (contractMatched.isNotEmpty()) {
            return ClassifyResult(DocTypes.SALES_CONTRACT, "匹配通用合同关键词(默认销售合同): ${contractMatched.joinToString(",")}")
        }

        return ClassifyResult(DocTypes.UNKNOWN, "未命中关键词规则")
    }
}
