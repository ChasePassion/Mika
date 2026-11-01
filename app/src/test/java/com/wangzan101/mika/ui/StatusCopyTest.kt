package com.wangzan101.mika.ui

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCopyTest {
    @get:Rule val texts = ChineseTexts()

    // 频道设置页打不开时依次退回,第一个能开的就停
    @Test
    fun firstThatStartsFallsBack() {
        val tried = mutableListOf<String>()
        assertTrue(firstThatStarts(listOf("channel", "app")) { tried += it; it == "app" })
        assertEquals(listOf("channel", "app"), tried)
        tried.clear()
        assertTrue(firstThatStarts(listOf("channel", "app")) { tried += it; true })
        assertEquals(listOf("channel"), tried)
        assertFalse(firstThatStarts(listOf("channel", "app")) { false })
    }
}
