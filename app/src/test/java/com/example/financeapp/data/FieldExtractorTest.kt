package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldExtractorTest {

    private val extractor = FieldExtractor()

    @Test
    fun salesContract_tableRow_extractsProductModelQuantityPriceAndTotal() {
        val text = """
            销售合同
            合同编号：SC-2026-001
            需方：天津龙创恒盛实业有限公司
            签订日期：2026-03-01
            产品名称 型号 数量 含税单价(RMB) 小计(RMB)
            工业传感器 XH-9 12 613.00 7,356.00
            合计：7,356.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.SALES_CONTRACT, text)

        assertEquals("SC-2026-001", result.fields.contractNo)
        assertEquals("天津龙创恒盛实业有限公司", result.fields.counterpartyName)
        assertEquals("工业传感器", result.fields.productName)
        assertEquals("XH-9", result.fields.productModel)
        assertEquals("12", result.fields.quantity)
        assertEquals("613.00", result.fields.unitPrice)
        assertEquals("7356.00", result.fields.totalAmount)
    }

    @Test
    fun purchaseContract_tableRow_extractsProductModelQuantityPriceAndTotal() {
        val text = """
            采购合同
            甲方：浙江拜伦智能科技有限公司
            供方：苏州新科贸昜有限公司
            日期：2026年04月10日
            名称 规格/型号 数量 单价 总金额
            泵体组件 PB-20A 3 1,860.00 5,580.00
            合计：5,580.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.PURCHASE_CONTRACT, text)

        assertNull(result.fields.contractNo)
        assertEquals("苏州新科贸昜有限公司", result.fields.counterpartyName)
        assertEquals("泵体组件", result.fields.productName)
        assertEquals("PB-20A", result.fields.productModel)
        assertEquals("3", result.fields.quantity)
        assertEquals("1860.00", result.fields.unitPrice)
        assertEquals("5580.00", result.fields.totalAmount)
    }

    @Test
    fun receiptSlip_extractsPayerAsCounterparty_amountAndTransactionTime() {
        val text = """
            网商银行电子回单
            收款户名：浙江拜伦智能科技有限公司
            付款户名：天津龙创恒盛实业有限公司
            交易时间：2026-04-15 10:22:30
            转账金额：￥5,861.00
            收款账号：6222334455667788
            流水号：202604151022300001
        """.trimIndent()

        val result = extractor.extract(DocTypes.RECEIPT, text)

        assertEquals("天津龙创恒盛实业有限公司", result.fields.counterpartyName)
        assertEquals("2026-04-15 10:22:30", result.fields.transactionDate)
        assertEquals("5861.00", result.fields.amount)
        assertEquals(DocTypes.RECEIPT, result.fields.direction)
    }

    @Test
    fun paymentSlip_extractsPayeeAsCounterparty_amountAndTransactionTime() {
        val text = """
            网商银行电子回单
            付款户名：浙江拜伦智能科技有限公司
            收款户名：北京云启科技有限公司
            交易时间：2026/04/16 09:10:12
            交易金额：¥3,595.75
            付款账号：6217001234567890123
            手机号：13800138000
        """.trimIndent()

        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals("北京云启科技有限公司", result.fields.counterpartyName)
        assertTrue(result.fields.transactionDate?.contains("2026/04/16") == true)
        assertEquals("3595.75", result.fields.amount)
        assertEquals(DocTypes.PAYMENT, result.fields.direction)
    }

    @Test
    fun transferSlip_shouldNotTreatAccountOrSerialAsAmount() {
        val text = """
            收款户名：北京云启科技有限公司
            付款户名：浙江拜伦智能科技有限公司
            交易时间：2026-04-17 11:30:00
            付款账号：6222021234567890123
            流水号：202604171130000000987654
            转账金额：613.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals("613.00", result.fields.amount)
    }
}
