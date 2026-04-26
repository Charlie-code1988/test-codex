package com.example.financeapp.data

data class ClassifyResult(
    val docType: String,
    val reason: String
)

class DocTypeClassifier(
    private val companyName: String = "浙江拜伦智能科技有限公司"
) {

    fun classify(text: String): ClassifyResult {
        val normalized = text.lowercase()
        if (normalized.isBlank()) {
            return ClassifyResult(DocTypes.UNKNOWN, "文本为空；scores: SALES=0, PURCHASE=0, RECEIPT=0, PAYMENT=0")
        }

        val salesScore = ScoreBucket()
        val purchaseScore = ScoreBucket()
        val receiptScore = ScoreBucket()
        val paymentScore = ScoreBucket()

        val contractStrongKeywords = listOf("合同", "购销合同", "采购合同", "销售合同", "甲方", "乙方", "供方", "需方", "合同编号")
        val contractHits = contractStrongKeywords.filter { normalized.contains(it) }

        if (contractHits.isNotEmpty()) {
            if (normalized.contains("销售合同")) salesScore.add(5, "销售合同")
            if (normalized.contains("采购合同")) purchaseScore.add(5, "采购合同")
            if (normalized.contains("购销合同")) {
                salesScore.add(2, "购销合同")
                purchaseScore.add(2, "购销合同")
            }

            val hasCompany = normalized.contains(companyName.lowercase())
            val myAsSeller = hasCompany && containsAny(normalized, listOf("供方", "卖方", "乙方"))
            val myAsBuyer = hasCompany && containsAny(normalized, listOf("需方", "买方", "采购方", "甲方"))

            if (myAsSeller) salesScore.add(4, "我司为供方/卖方/乙方")
            if (myAsBuyer) purchaseScore.add(4, "我司为需方/买方/采购方")

            if (containsAny(normalized, listOf("需方", "买方"))) salesScore.add(1, "存在需方/买方角色")
            if (containsAny(normalized, listOf("供方", "卖方", "供应商"))) purchaseScore.add(1, "存在供方/卖方角色")

            val finalType = when {
                salesScore.score > purchaseScore.score -> DocTypes.SALES_CONTRACT
                purchaseScore.score > salesScore.score -> DocTypes.PURCHASE_CONTRACT
                normalized.contains("购销合同") -> DocTypes.SALES_CONTRACT
                normalized.contains("采购合同") -> DocTypes.PURCHASE_CONTRACT
                normalized.contains("销售合同") -> DocTypes.SALES_CONTRACT
                else -> DocTypes.SALES_CONTRACT
            }

            val defaultReason = if (normalized.contains("购销合同") && salesScore.score == purchaseScore.score) {
                "购销合同但角色不明确，默认销售合同"
            } else {
                "合同强特征优先，忽略合同条款中的收付款关键词"
            }

            return ClassifyResult(
                finalType,
                buildReason(
                    prefix = "合同强特征命中: ${contractHits.joinToString(",")}; $defaultReason",
                    sales = salesScore,
                    purchase = purchaseScore,
                    receipt = receiptScore,
                    payment = paymentScore
                )
            )
        }

        applyReceiptScoring(normalized, receiptScore)
        applyPaymentScoring(normalized, paymentScore)

        val finalType = when {
            receiptScore.score >= paymentScore.score && receiptScore.score >= 2 -> DocTypes.RECEIPT
            paymentScore.score > receiptScore.score && paymentScore.score >= 2 -> DocTypes.PAYMENT
            else -> DocTypes.UNKNOWN
        }

        val why = when (finalType) {
            DocTypes.RECEIPT -> "命中收款/入账语义"
            DocTypes.PAYMENT -> "命中付款/支出语义"
            else -> "未命中有效规则"
        }

        return ClassifyResult(
            finalType,
            buildReason(
                prefix = why,
                sales = salesScore,
                purchase = purchaseScore,
                receipt = receiptScore,
                payment = paymentScore
            )
        )
    }

    private fun applyReceiptScoring(text: String, score: ScoreBucket) {
        addHits(score, text, listOf("收款", "收据", "已收", "回款", "银行回单收入", "转入", "收入", "入账", "收到", "对方户名", "货款", "余额"), 2)
        if (text.contains("向你转账") || text.contains("转入你") || text.contains("收到转账")) {
            score.add(3, "转入到你/收到转账语义")
        }
    }

    private fun applyPaymentScoring(text: String, score: ScoreBucket) {
        addHits(score, text, listOf("付款", "支付", "已付", "银行回单支出", "转出", "支出", "支付成功", "付款成功", "付款人"), 2)
        if (text.contains("你向") && (text.contains("转账") || text.contains("付款") || text.contains("支付"))) {
            score.add(3, "你向他方付款/转账语义")
        }
        if (text.contains("向") && text.contains("付款")) {
            score.add(2, "向某方付款语义")
        }
    }

    private fun addHits(bucket: ScoreBucket, text: String, keywords: List<String>, weight: Int) {
        keywords.filter { text.contains(it) }.forEach { bucket.add(weight, it) }
    }

    private fun containsAny(text: String, words: List<String>): Boolean {
        return words.any { text.contains(it) }
    }

    private fun buildReason(
        prefix: String,
        sales: ScoreBucket,
        purchase: ScoreBucket,
        receipt: ScoreBucket,
        payment: ScoreBucket
    ): String {
        return "$prefix；" +
            "scores: SALES=${sales.score}, PURCHASE=${purchase.score}, RECEIPT=${receipt.score}, PAYMENT=${payment.score}；" +
            "matched: SALES=[${sales.hits.joinToString(",")}], PURCHASE=[${purchase.hits.joinToString(",")}], " +
            "RECEIPT=[${receipt.hits.joinToString(",")}], PAYMENT=[${payment.hits.joinToString(",")}]"
    }

    private class ScoreBucket {
        var score: Int = 0
        val hits = mutableListOf<String>()

        fun add(value: Int, reason: String) {
            score += value
            hits += reason
        }
    }
}
