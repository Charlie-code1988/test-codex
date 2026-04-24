package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextProcessorTest {

    private val processor = OcrTextProcessor()

    @Test
    fun pureChinese_chineseBetter_finalUsesChinese() {
        val latin = "RIAt T2035\nEEAEEE"
        val chinese = "本合同自双方签字之日起生效\n有效期一年"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertEquals(processor.cleanText(chinese, preferChinese = true), final)
        assertFalse(final.contains("RIAt"))
        assertFalse(final.contains("EEAEEE"))
    }

    @Test
    fun pureEnglish_latinBetter_finalUsesLatin() {
        val latin = "Purchase Contract\nModel AB-123\nQty 8\nPrice 750"
        val chinese = "采夠 合同\n型号"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertEquals(processor.cleanText(latin, preferChinese = false), final)
    }

    @Test
    fun tableNumbers_doNotMergeIntoWrongBigNumber() {
        val latin = "EGH20CA2R3250Z0C\nEGH15CA2U1600Z0C\nEGH20CA2R1600Z0C\n4\n750\n400\n462"
        val chinese = latin

        val final = processor.buildFinalOcrText(latin, chinese)

        assertFalse(final.contains("4750"))
        assertFalse(final.contains("400462"))
        assertTrue(final.contains("\n4\n"))
        assertTrue(final.contains("\n750\n"))
    }

    @Test
    fun garbageTokens_shouldNotPolluteChineseFinal() {
        val latin = "RIAt T2035 áT2035Æ#E EEAEEE"
        val chinese = "项目进度如下\n请尽快确认"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertFalse(final.contains("RIAt"))
        assertFalse(final.contains("EEAEEE"))
        assertTrue(final.contains("项目进度如下"))
    }

    @Test
    fun modelToken_shouldBePreserved() {
        val latin = "EGH20CA2R3250Z0C"
        val chinese = ""

        val final = processor.buildFinalOcrText(latin, chinese)

        assertTrue(final.contains("EGH20CA2R3250Z0C"))
    }
}
