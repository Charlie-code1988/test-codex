package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldExtractorTest {

    private val extractor = FieldExtractor()

    @Test
    fun salesContract_multiLineTable_extractsAllLineItemsAndCalculatedTotal() {
        val text = """
            销售合同
            合同编号：SC-2026-001
            需方：天津龙创恒盛实业有限公司
            产品名称 | 型号 | 数量 | 单价 | 小计
            HIWIN直线导轨 | HGH55HA2R1620ZAC | 2 | 2110 | 4220
            HIWIN滚珠丝杆 | R50-10T4-FSI-1021-0.05 | 1 | 1219 | 1219
            支撑座 | BK40-C5 | 1 | 390 | 390
            支撑座 | BF40 | 1 | 171 | 171
            税号：91330106MA27X12345
            账号：622233445566778899
        """.trimIndent()

        val result = extractor.extract(DocTypes.SALES_CONTRACT, text)

        assertEquals(4, result.fields.lineItems.size)
        assertEquals("HIWIN直线导轨", result.fields.lineItems[0].productName)
        assertEquals("HGH55HA2R1620ZAC", result.fields.lineItems[0].productModel)
        assertEquals("2", result.fields.lineItems[0].quantity)
        assertEquals("2110", result.fields.lineItems[0].unitPrice)
        assertEquals("4220", result.fields.lineItems[0].lineTotal)
        assertEquals("6000", result.fields.totalAmount)
    }

    @Test
    fun purchaseContract_multiLineTable_extractsLineItemsAndSkipsNoiseRows() {
        val text = """
            采购合同
            甲方：浙江拜伦智能科技有限公司
            供方：苏州新科贸昜有限公司
            名称 规格/型号 数量 单价 总金额
            泵体组件 PB-20A 3 1860 5580
            连接件 LK-7 2 140 280
            电话：13800138000
            地址：杭州市西湖区xxx路55号
        """.trimIndent()

        val result = extractor.extract(DocTypes.PURCHASE_CONTRACT, text)

        assertEquals(2, result.fields.lineItems.size)
        assertEquals("泵体组件", result.fields.lineItems[0].productName)
        assertEquals("PB-20A", result.fields.lineItems[0].productModel)
        assertEquals("3", result.fields.lineItems[0].quantity)
        assertEquals("1860", result.fields.lineItems[0].unitPrice)
        assertEquals("5580", result.fields.lineItems[0].lineTotal)
        assertEquals("5860", result.fields.totalAmount)
    }

    @Test
    fun receiptSlip_extractsCounterpartyDateAndAmount() {
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
    fun paymentSlip_extractsCounterpartyDateAndAmount() {
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
    fun amountShouldNotUseContractNoTaxNoPhoneOrSerial() {
        val text = """
            合同编号：SC-2026-55
            税号：91330106MA27X12345
            手机号：13800138000
            流水号：202604171416070000123456
            转账金额：613.00
        """.trimIndent()

        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals("613.00", result.fields.amount)
    }

    @Test
    fun messyOcrLines_shouldStillExtractAtLeastOneLineItem() {
        val text = """
            销售合同
            型号 数量 单价 小计
            HIWIN直线导轨
            HGH55HA2R1620ZAC 2
            2110 4220
            税号 91330106MA27X12345
        """.trimIndent()

        val result = extractor.extract(DocTypes.SALES_CONTRACT, text)

        assertTrue(result.fields.lineItems.isNotEmpty())
    }
}
