package com.voiceink.app.ai.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentHintsTest {
    @Test
    fun `note 和 merge 强制保留笔记而普通输入不强制`() {
        assertTrue(requiresNoteIntent("note"))
        assertTrue(requiresNoteIntent("merge"))
        assertFalse(requiresNoteIntent("todo"))
        assertFalse(requiresNoteIntent(null))
    }
}
