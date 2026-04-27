package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldExtractorTest {

    private val extractor = FieldExtractor()

    @Test
    fun salesContract_multiLineTable_extractsLineItemsAndTotal() {
        val text = """
            销售合同
            合同编号：SC-2026-001
            需方：天津龙创恒盛实业有限公司
            签订日期：2026-03-01
            产品名称 型号 数量 含税单价(RMB) 小计(RMB)
            工业传感器 XH-9 12 613.00 7,356.00
            控制模块 CM-2 3 1,860.00 5,580.00
            合计：12,936.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.SALES_CONTRACT, text)

        assertEquals(2, result.fields.lineItems.size)
        assertEquals("工业传感器", result.fields.lineItems[0].productName)
        assertEquals("XH-9", result.fields.lineItems[0].productModel)
        assertEquals("12", result.fields.lineItems[0].quantity)
        assertEquals("613.00", result.fields.lineItems[0].unitPrice)
        assertEquals("7356.00", result.fields.lineItems[0].lineTotal)
        assertEquals("12936.00", result.fields.totalAmount)
    }

    @Test
    fun purchaseContract_multiLineTable_extractsLineItemsAndTotal() {
        val text = """
            采购合同
            甲方：浙江拜伦智能科技有限公司
            供方：苏州新科贸昜有限公司
            日期：2026年04月10日
            名称 规格/型号 数量 单价 总金额
            泵体组件 PB-20A 3 1,860.00 5,580.00
            连接件 LK-7 2 140.00 280.00
            总金额：5,860.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.PURCHASE_CONTRACT, text)

        assertEquals(2, result.fields.lineItems.size)
        assertEquals("泵体组件", result.fields.lineItems[0].productName)
        assertEquals("PB-20A", result.fields.lineItems[0].productModel)
        assertEquals("3", result.fields.lineItems[0].quantity)
        assertEquals("1860.00", result.fields.lineItems[0].unitPrice)
        assertEquals("5580.00", result.fields.lineItems[0].lineTotal)
        assertEquals("5860.00", result.fields.totalAmount)
    }

    @Test
    fun receiptSlip_extractsCounterpartyDateAmount() {
        val text = """
            网商银行电子回单
            收款户名：浙江拜伦智能科技有限公司
            付款户名：天津龙创恒盛实业有限公司
            交易时间：2026-04-17 14:16:07
            转账金额：￥5,861.00
            付款账号：6222021234567890123
            流水号：202604171416070000123456
        """.trimIndent()

        val result = extractor.extract(DocTypes.RECEIPT, text)

        assertEquals(DocTypes.RECEIPT, result.fields.direction)
        assertEquals("天津龙创恒盛实业有限公司", result.fields.counterpartyName)
        assertEquals("2026-04-17 14:16:07", result.fields.transactionDate)
        assertEquals("5861.00", result.fields.amount)
    }

    @Test
    fun paymentSlip_extractsCounterpartyDateAmount() {
        val text = """
            网商银行电子回单
            付款户名：浙江拜伦智能科技有限公司
            收款账户户名：北京云启科技有限公司
            支付时间：2026/04/17
            交易金额：¥3595.75
            手机号：13800138000
        """.trimIndent()

        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals(DocTypes.PAYMENT, result.fields.direction)
        assertEquals("北京云启科技有限公司", result.fields.counterpartyName)
        assertTrue(result.fields.transactionDate?.contains("2026/04/17") == true)
        assertEquals("3595.75", result.fields.amount)
    }

    @Test
    fun shouldNotTreatAccountSerialTaxNoPhoneAsAmount() {
        val text = """
            收款户名：北京云启科技有限公司
            付款户名：浙江拜伦智能科技有限公司
            税号：91330106MA27X12345
            付款账号：622233445566778899
            手机号：13800138000
            流水号：202604171416070000123456
            转账金额：613.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals("613.00", result.fields.amount)
    }
}
