package com.voiceink.app.ui.home

import com.voiceink.app.data.local.dao.NoteLinkPair
import com.voiceink.app.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListGroupingTest {
    @Test
    fun relatedNotesStayTogetherInOneConnectedGroup() {
        val notes = listOf(
            note(3, "Third", 300L),
            note(2, "Second", 200L),
            note(1, "First", 100L)
        )
        val links = listOf(
            NoteLinkPair(1, 2),
            NoteLinkPair(2, 1),
            NoteLinkPair(2, 3),
            NoteLinkPair(3, 2)
        )

        val sections = buildNoteSections(notes, NoteListMode.RELATED, links)

        assertEquals(1, sections.size)
        assertEquals("关联组 1 · Third", sections.single().label)
        assertEquals(listOf(3L, 2L, 1L), sections.single().notes.map { it.id })
    }

    @Test
    fun unlinkingDoesNotRemoveNotesFromTheList() {
        val notes = listOf(note(2, "Second", 200L), note(1, "First", 100L))
        val linked = listOf(NoteLinkPair(1, 2), NoteLinkPair(2, 1))

        val linkedSections = buildNoteSections(notes, NoteListMode.RELATED, linked)
        val unlinkedSections = buildNoteSections(notes, NoteListMode.RELATED, emptyList())

        assertEquals(1, linkedSections.size)
        assertEquals("未关联", unlinkedSections.single().label)
        assertEquals(setOf(1L, 2L), unlinkedSections.single().notes.map { it.id }.toSet())
    }

    @Test
    fun folderModeIncludesUncategorizedNotes() {
        val notes = listOf(
            note(1, "Product", 100L, "产品"),
            note(2, "Loose", 90L),
            note(3, "Reading", 80L, "阅读")
        )

        val sections = buildNoteSections(notes, NoteListMode.FOLDER, emptyList())

        assertEquals(listOf("文件夹 · 产品", "文件夹 · 阅读", "文件夹 · 未分类"), sections.map { it.label })
        assertTrue(sections.all { it.notes.isNotEmpty() })
    }

    @Test
    fun aggregateModeKeepsAllNotesVisible() {
        val notes = listOf(
            note(3, "Third", 300L),
            note(2, "Second", 200L),
            note(1, "First", 100L)
        )

        val sections = buildNoteSections(notes, NoteListMode.AGGREGATE, emptyList())

        assertEquals(3, sections.sumOf { it.notes.size })
        assertEquals(setOf(1L, 2L, 3L), sections.flatMap { it.notes }.map { it.id }.toSet())
    }

    private fun note(id: Long, title: String, createdAt: Long, category: String? = null) =
        NoteEntity(id = id, title = title, content = title, category = category, createdAt = createdAt, updatedAt = createdAt)
}
