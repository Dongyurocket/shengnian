package com.voiceink.app.ai.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentHintsTest {
    @Test
    fun `note 和 merge 强制保留笔记而普通输入不强制`() {
        assertTrue(requiresNoteIntent("note"))
        assertTrue(requiresNoteIntent("note_plain"))
        assertTrue(requiresNoteIntent("merge"))
        assertFalse(requiresNoteIntent("todo"))
        assertFalse(requiresNoteIntent(null))
    }

    @Test
    fun `灵感显式标记生成对应提示`() {
        assertEquals("用户明确标记为灵感，请务必输出 is_inspiration=true。", inspirationHint("note"))
        assertEquals("用户明确标记为普通记录，请务必输出 is_inspiration=false。", inspirationHint("note_plain"))
        assertNull(inspirationHint(null))
        assertNull(inspirationHint("merge"))
    }
}
