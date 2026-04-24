package com.example.financeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextProcessorTest {

    private val processor = OcrTextProcessor()

    @Test
    fun english_finalShouldKeepLeadingThe() {
        val latin = "The most difficult part...\nis consistency."
        val chinese = "最 困难 部分"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertTrue(final.startsWith("The most difficult part"))
    }

    @Test
    fun chineseShouldNotBePollutedByLatinGarbage() {
        val latin = "RIAt T2035 EEAEEE"
        val chinese = "项目进度如下\n请尽快确认"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertEquals(processor.cleanText(chinese), final)
        assertFalse(final.contains("RIAt"))
        assertFalse(final.contains("EEAEEE"))
    }

    @Test
    fun tableNumbers_shouldNotMerge() {
        val latin = "EGH20CA2R3250Z0C\nEGH15CA2U1600Z0C\nEGH20CA2R1600Z0C\n4\n750\n400\n462"
        val chinese = latin

        val final = processor.buildFinalOcrText(latin, chinese)

        assertFalse(final.contains("4750"))
        assertFalse(final.contains("400462"))
        assertTrue(final.contains("\n4\n"))
        assertTrue(final.contains("\n750\n"))
    }

    @Test
    fun chineseBetter_finalEqualsCleanedChinese() {
        val latin = "RIAt T2035"
        val chinese = "本合同自双方签字之日起生效\n有效期一年"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertEquals(processor.cleanText(chinese), final)
    }

    @Test
    fun latinBetter_finalEqualsCleanedLatin() {
        val latin = "Purchase Contract\nModel AB-123\nQty 8\nPrice 750"
        val chinese = "采夠 合同"

        val final = processor.buildFinalOcrText(latin, chinese)

        assertEquals(processor.cleanText(latin), final)
    }
}
