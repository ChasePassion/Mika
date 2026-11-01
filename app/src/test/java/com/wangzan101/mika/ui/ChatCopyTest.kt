package com.wangzan101.mika.ui

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.chat.ConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ChatCopyTest {
    @get:Rule val texts = ChineseTexts()

    @Test fun sixDistinctNonBlankExamples() {
        assertEquals(6, ChatCopy.examples.size)
        assertEquals(6, ChatCopy.examples.map { it.text }.filter { it.isNotBlank() }.toSet().size)
    }

    @Test fun titleIsTheFirstLineOfTheFirstMessage() {
        val first = Exchange("查车次\n附件:/workspace/inbox/a.pdf(1 KB)", emptyList(), ExchangeStatus.Working, live = true)
        assertEquals("查车次", ChatCopy.title(listOf(first, first.copy(goal = "再查"))))
        assertEquals("", ChatCopy.title(emptyList()))
        // 只发了附件:不拿路径当标题
        assertEquals("照片", ChatCopy.title(listOf(first.copy(goal = "附件:/workspace/inbox/a.jpg(1 KB)"))))
        assertEquals("a.pdf", ChatCopy.plainTitle("附件:/workspace/inbox/a.pdf(1 KB)"))
        assertEquals(ScreenCopy.TAG, ChatCopy.plainTitle("[[screen:s1]]"))
        // 在浏览器页上对着一页问的:页面说明不当标题
        val paged = com.wangzan101.mika.core.cap.web.AttachedPage.compose(com.wangzan101.mika.core.cap.web.AttachedPage.Page("https://a.example/", "页"), "挑一个\n第二行")
        assertEquals("挑一个", ChatCopy.plainTitle(paged))
    }

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun drawerListsRunningThenRecent() {
        val now = at(2026, 10, 27, 15)
        val all = listOf(
            ConversationSummary("a", "附近的川菜", at(2026, 10, 27, 14)),
            ConversationSummary("b", "明天的高铁票", at(2026, 10, 26)),
            ConversationSummary("c", "PDF 要点整理", at(2026, 10, 26)),
        )
        val current = CurrentConversation("a", running = true, title = "附近的川菜", subtitle = "正在操作外卖")
        val s = drawerSections(
            all, current, others = listOf("c", "d"), otherTitle = { if (it == "d") "第二个任务" else null },
            canSwitch = true, now = now, zone = zone,
        )
        assertEquals(listOf("a", "c", "d"), s.running.map { it.id })
        assertEquals(listOf("附近的川菜", "PDF 要点整理", "第二个任务"), s.running.map { it.title })
        assertEquals("正在操作外卖", s.running.first().subtitle)
        assertTrue(s.running.first().current)
        assertEquals(listOf("b"), s.recent.map { it.id })
        assertEquals("", s.recent.single().subtitle)
    }

    /** 置顶的对话在「置顶」一段,最近置顶的在前,不在「最近」里重复;有任务在跑的只列在进行中(带图钉)。 */
    @Test fun drawerListsPinnedAboveRecent() {
        val all = listOf(
            ConversationSummary("a", "一", 40L),
            ConversationSummary("b", "二", 30L, pinnedAt = 100L),
            ConversationSummary("c", "三", 20L),
            ConversationSummary("d", "四", 10L, pinnedAt = 200L),
            ConversationSummary("e", "五", 5L, pinnedAt = 300L),
        )
        val s = drawerSections(all, CurrentConversation("a", false, "一", ""), others = listOf("e"), { null }, canSwitch = true, now = 0L, zone = zone)
        assertEquals(listOf("e"), s.running.map { it.id })
        assertTrue(s.running.single().pinned)
        assertEquals(listOf("d", "b"), s.pinned.map { it.id })
        assertTrue(s.pinned.all { it.pinned })
        assertEquals(listOf("a", "c"), s.recent.map { it.id })
        assertTrue(s.recent.none { it.pinned })
        // 取消置顶:回到「最近」里按改动时间排的位置
        val unpinned = drawerSections(all.map { if (it.id == "b") it.copy(pinnedAt = null) else it }, CurrentConversation("a", false, "一", ""), emptyList(), { null }, true, 0L, zone)
        assertEquals(listOf("e", "d"), unpinned.pinned.map { it.id })
        assertEquals(listOf("a", "b", "c"), unpinned.recent.map { it.id })
    }

    /** 在浏览器页上对着一页开始的对话:侧栏那一条带着网站(画成「网页」小标签和网站名),进行中、最近两段都是。 */
    @Test fun drawerMarksConversationsStartedFromAPage() {
        val page = com.wangzan101.mika.chat.PageOrigin("https://shop.example.com/s?k=1", "shop.example.com", "搜索")
        val all = listOf(ConversationSummary("a", "一", 0L, page = page), ConversationSummary("b", "二", 0L), ConversationSummary("c", "三", 0L, page = page))
        val s = drawerSections(all, CurrentConversation("x", false, "", ""), others = listOf("c"), { null }, canSwitch = true, now = 0L, zone = zone)
        assertEquals(listOf("shop.example.com", null), s.recent.map { it.site })
        assertEquals("shop.example.com", s.running.single().site)
    }

    /** 任务进行中、没开并行:只有当前对话能点。 */
    @Test fun drawerLocksOtherConversationsWhileBusy() {
        val all = listOf(ConversationSummary("a", "一", 0L), ConversationSummary("b", "二", 0L))
        val s = drawerSections(all, CurrentConversation("a", false, "一", ""), emptyList(), { null }, canSwitch = false, now = 0L, zone = zone)
        assertEquals(listOf(true, false), s.recent.map { it.enabled })
        assertEquals(listOf(true, false), s.recent.map { it.current })
        // 当前对话还没存进列表时(刚发出第一句)也列在进行中
        val fresh = drawerSections(emptyList(), CurrentConversation(null, true, "", "正在处理"), emptyList(), { null }, false, 0L, zone)
        assertEquals("新对话", fresh.running.single().title)
        assertTrue(fresh.running.single().enabled)
    }
}
