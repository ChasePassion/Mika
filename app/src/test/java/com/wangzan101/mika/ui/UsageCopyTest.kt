package com.wangzan101.mika.ui

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.core.usage.UsageCounts
import com.wangzan101.mika.core.usage.UsageKey
import com.wangzan101.mika.core.usage.UsagePurpose
import com.wangzan101.mika.core.usage.UsageQuery
import com.wangzan101.mika.core.usage.UsageRange
import com.wangzan101.mika.core.usage.UsageRow
import com.wangzan101.mika.core.usage.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UsageCopyTest {
    @get:Rule val texts = ChineseTexts()

    private val today = LocalDate.of(2026, 10, 27)

    private val now = Instant.parse("2026-10-27T10:00:00Z")
    private val h = now.toEpochMilli() / 3_600_000
    private val snap = UsageSnapshot(
        listOf(
            UsageRow(h, UsageKey("p1", "a", UsagePurpose.Main), UsageCounts(input = 100, calls = 1)),
            UsageRow(h, UsageKey("p1", "b", UsagePurpose.Light), UsageCounts(input = 10, calls = 1)),
            UsageRow(h, UsageKey("p2", "c", UsagePurpose.Main), UsageCounts(input = 50, calls = 1)),
            UsageRow(h, UsageKey("p3", "d", UsagePurpose.Main), UsageCounts(input = 40, calls = 1)),
            UsageRow(h, UsageKey("p4", "e", UsagePurpose.Main), UsageCounts(input = 1, calls = 1)),
        ),
        mapOf("p2" to "旧服务商"),
    )
    private val report = UsageQuery.report(snap, UsageRange.LastDays(7), now, ZoneOffset.UTC, linkedMapOf("p1" to "甲", "p3" to "丙", "p4" to "丁"))

    @Test fun modelRankingIsAllProvidersOrJustTheSelectedOne() {
        val all = UsageCopy.modelRanking(report, null)
        assertEquals(listOf("a", "c", "d", "b", "e"), all.map { it.model.model })
        assertEquals(listOf("p1", "p2", "p3", "p1", "p4"), all.map { it.provider.id })
        assertEquals(100.0 / 201, all.first().share, 1e-9)
        val p1 = UsageCopy.modelRanking(report, "p1")
        assertEquals(listOf("a", "b"), p1.map { it.model.model })
        assertEquals("占比按这一排算", 100.0 / 110, p1.first().share, 1e-9)
    }

    @Test fun colorsFollowTheFixedOrderAndFoldBeyondThree() {
        // 连着的按连接先后:甲 0、丙 1、丁 2,删掉的排在后面
        assertEquals(mapOf("p1" to 0, "p3" to 1, "p4" to 2, "p2" to null), report.providers.associate { it.id to UsageCopy.colorSlot(it) })
    }

    @Test fun niceMaxGivesRoundGridlines() {
        assertEquals(1, UsageCopy.niceMax(0))
        assertEquals(1, UsageCopy.niceMax(1))
        assertEquals(10, UsageCopy.niceMax(9))
        assertEquals(20, UsageCopy.niceMax(11))
        assertEquals(400_000, UsageCopy.niceMax(312_345))
        assertEquals(1_000_000, UsageCopy.niceMax(812_345))
        assertEquals(1_000_000, UsageCopy.niceMax(1_000_000))
    }
}
