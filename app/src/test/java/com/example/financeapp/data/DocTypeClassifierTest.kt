package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocTypeClassifierTest {

    private val classifier = DocTypeClassifier()

    @Test
    fun fullSalesContract_withPaymentClause_stillSalesContract() {
        val text = "销售合同 合同编号 甲方 乙方 供方 需方 付款条款 付款方式"
        val result = classifier.classify(text)
        assertEquals(DocTypes.SALES_CONTRACT, result.docType)
        assertTrue(result.reason.contains("合同强特征"))
    }

    @Test
    fun purchaseContract_shouldBePurchaseContract() {
        val text = "采购合同 采购方 供应商 应付"
        assertEquals(DocTypes.PURCHASE_CONTRACT, classifier.classify(text).docType)
    }

    @Test
    fun bankReceiptSlip_shouldBeReceipt() {
        val text = "银行回单收入 收款人 交易时间 金额 已收"
        assertEquals(DocTypes.RECEIPT, classifier.classify(text).docType)
    }

    @Test
    fun paymentSuccessScreenshot_shouldBePayment() {
        val text = "支付成功 付款人 交易时间 金额 转出"
        assertEquals(DocTypes.PAYMENT, classifier.classify(text).docType)
    }

    @Test
    fun bankSmsIncome_shouldBeReceipt() {
        val text = "【银行短信】货款转入，收入金额1000元，对方户名张三，当前余额..."
        assertEquals(DocTypes.RECEIPT, classifier.classify(text).docType)
    }

    @Test
    fun unrelatedText_shouldBeUnknown() {
        val text = "这是一个没有命中关键词的普通说明"
        assertEquals(DocTypes.UNKNOWN, classifier.classify(text).docType)
    }

    @Test
    fun purchaseSellContract_roleUnknown_defaultSalesWithReason() {
        val text = "购销合同 合同编号 甲方 乙方"
        val result = classifier.classify(text)
        assertEquals(DocTypes.SALES_CONTRACT, result.docType)
        assertTrue(result.reason.contains("购销合同但角色不明确，默认销售合同"))
    }
}
