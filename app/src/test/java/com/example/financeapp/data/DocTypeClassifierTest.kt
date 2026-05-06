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
    fun payeeIsMyCompany_shouldBeReceipt() {
        val text = "网商银行转账 收款人名称：浙江拜伦智能科技有限公司 金额1000"
        val result = classifier.classify(text)
        assertEquals(DocTypes.RECEIPT, result.docType)
        assertTrue(result.reason.contains("收款人名称为浙江拜伦智能科技有限公司"))
    }

    @Test
    fun payeeIsOtherCompany_shouldBePayment() {
        val text = "网商银行转账 收款人名称：天津龙创恒盛实业有限公司 我司名称：浙江拜伦智能科技有限公司"
        val result = classifier.classify(text)
        assertEquals(DocTypes.PAYMENT, result.docType)
        assertTrue(result.reason.contains("收款人名称为天津龙创恒盛实业有限公司，不是我司"))
    }

    @Test
    fun payerIsMyCompany_shouldBePayment() {
        val text = "付款人名称：浙江拜伦智能科技有限公司 转账成功"
        val result = classifier.classify(text)
        assertEquals(DocTypes.PAYMENT, result.docType)
        assertTrue(result.reason.contains("付款人名称为浙江拜伦智能科技有限公司"))
    }

    @Test
    fun payerIsOtherCompany_shouldBeReceipt() {
        val text = "付款人名称：天津龙创恒盛实业有限公司 备注货款"
        val result = classifier.classify(text)
        assertEquals(DocTypes.RECEIPT, result.docType)
        assertTrue(result.reason.contains("付款人名称为天津龙创恒盛实业有限公司，不是我司"))
    }

    @Test
    fun paymentSuccessScreenshot_shouldBePayment() {
        val text = "支付成功 付款人 交易时间 金额 转出"
        assertEquals(DocTypes.PAYMENT, classifier.classify(text).docType)
    }

    @Test
    fun bankSmsIncome_shouldBeReceipt() {
        val text = "【银行短信】货款转入，收入金额1000元，对方向你转账，当前余额..."
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
