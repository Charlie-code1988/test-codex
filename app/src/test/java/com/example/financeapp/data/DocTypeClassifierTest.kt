package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocTypeClassifierTest {

    private val classifier = DocTypeClassifier()

    @Test
    fun classify_salesContract() {
        val text = "销售合同 甲方 客户 应收 合同编号"
        assertEquals(DocTypes.SALES_CONTRACT, classifier.classify(text).docType)
    }

    @Test
    fun classify_purchaseContract() {
        val text = "采购合同 供应商 应付 采购方"
        assertEquals(DocTypes.PURCHASE_CONTRACT, classifier.classify(text).docType)
    }

    @Test
    fun classify_receipt() {
        val text = "银行回单收入 已收 回款 收据"
        assertEquals(DocTypes.RECEIPT, classifier.classify(text).docType)
    }

    @Test
    fun classify_payment() {
        val text = "银行回单支出 已付 付款 支付"
        assertEquals(DocTypes.PAYMENT, classifier.classify(text).docType)
    }

    @Test
    fun classify_unknown() {
        val text = "这是一个没有命中关键词的普通说明"
        assertEquals(DocTypes.UNKNOWN, classifier.classify(text).docType)
    }
}
