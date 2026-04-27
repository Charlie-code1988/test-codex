package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldExtractorTest {

    private val extractor = FieldExtractor()

    @Test
    fun salesContract_extractsContractNoCounterpartyAndAmount() {
        val text = "销售合同 合同编号：SC-2026-001 需方：天津龙创恒盛实业有限公司 产品名称：工业传感器 合同金额：128000.00 签订日期：2026-03-01"
        val result = extractor.extract(DocTypes.SALES_CONTRACT, text)

        assertEquals("SC-2026-001", result.fields.contractNo)
        assertEquals("天津龙创恒盛实业有限公司", result.fields.counterpartyName)
        assertEquals("128000.00", result.fields.totalAmount)
    }

    @Test
    fun purchaseContract_extractsContractNoCounterpartyAndAmount() {
        val text = "采购合同 合同号：PC2026A08 供方：深圳市中科贸易有限公司 甲方：浙江拜伦智能科技有限公司 合计：56000.50 日期：2026年04月10日"
        val result = extractor.extract(DocTypes.PURCHASE_CONTRACT, text)

        assertEquals("PC2026A08", result.fields.contractNo)
        assertEquals("深圳市中科贸易有限公司", result.fields.counterpartyName)
        assertEquals("56000.50", result.fields.totalAmount)
    }

    @Test
    fun receiptSlip_extractsAmountDateAndCounterparty() {
        val text = "网商银行回单 交易日期：2026-04-15 收款人名称：浙江拜伦智能科技有限公司 付款人名称：天津龙创恒盛实业有限公司 金额：10000.00"
        val result = extractor.extract(DocTypes.RECEIPT, text)

        assertEquals("10000.00", result.fields.amount)
        assertEquals("2026-04-15", result.fields.transactionDate)
        assertEquals("天津龙创恒盛实业有限公司", result.fields.counterpartyName)
        assertEquals(DocTypes.RECEIPT, result.fields.direction)
    }

    @Test
    fun paymentSlip_extractsAmountDateAndCounterparty() {
        val text = "转账成功 日期：2026/04/16 收款人名称：北京云启科技有限公司 付款人名称：浙江拜伦智能科技有限公司 交易金额：2300.00"
        val result = extractor.extract(DocTypes.PAYMENT, text)

        assertEquals("2300.00", result.fields.amount)
        assertTrue(result.fields.transactionDate?.contains("2026/04/16") == true)
        assertEquals("北京云启科技有限公司", result.fields.counterpartyName)
        assertEquals(DocTypes.PAYMENT, result.fields.direction)
    }
}
