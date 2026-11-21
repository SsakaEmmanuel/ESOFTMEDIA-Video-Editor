package com.esoftmedia.videoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditorTest {
    private fun clip(id: String, duration: Long = 10_000L) =
        TimelineClip(id, "content://media/" + id, duration)

    @Test fun splitAtPlayheadCreatesTwoContiguousSourceRanges() {
        val original = clip("a")
        val state = TimelineState(listOf(original), playheadMs = 4_000L)
        val split = TimelineEditor.splitAtPlayhead(state, "a")

        assertEquals(2, split.clips.size)
        assertEquals(0L, split.clips[0].sourceInMs)
        assertEquals(4_000L, split.clips[0].sourceOutMs)
        assertEquals(4_000L, split.clips[1].sourceInMs)
        assertEquals(10_000L, split.clips[1].sourceOutMs)
        assertEquals(10_000L, split.durationMs)
    }

    @Test fun trimUsesSourceMillisecondsNotPercentages() {
        val trimmed = TimelineEditor.trim(TimelineState(listOf(clip("a"))), "a", 1_250L, 8_750L)
        assertEquals(1_250L, trimmed.clips.single().sourceInMs)
        assertEquals(8_750L, trimmed.clips.single().sourceOutMs)
        assertEquals(7_500L, trimmed.durationMs)
    }

    @Test fun reorderAndDuplicateKeepClipData() {
        val state = TimelineState(listOf(clip("a"), clip("b")))
        val moved = TimelineEditor.move(state, 0, 1)
        assertEquals("b", moved.clips[0].id)
        val duplicated = TimelineEditor.duplicate(moved, "b")
        assertEquals(3, duplicated.clips.size)
        assertEquals("b", duplicated.clips[1].id)
        assertEquals(10_000L, duplicated.clips[2].timelineDurationMs)
    }

    @Test fun snapOnlyAppliesWithinTolerance() {
        assertEquals(1_000L, TimelineEditor.snap(1_075L, listOf(1_000L)))
        assertEquals(1_300L, TimelineEditor.snap(1_300L, listOf(1_000L)))
    }

    @Test fun historySupportsUndoAndRedo() {
        val history = TimelineHistory()
        history.commit(TimelineState(listOf(clip("a"))))
        assertTrue(history.canUndo)
        assertEquals(0, history.undo().clips.size)
        assertTrue(history.canRedo)
        assertEquals(1, history.redo().clips.size)
        assertFalse(history.canRedo)
    }
}
