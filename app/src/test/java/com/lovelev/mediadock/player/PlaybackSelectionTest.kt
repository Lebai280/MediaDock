package com.lovelev.mediadock.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 选择令牌：区分「新选中条目」与「旋屏后重新进入」。 */
class PlaybackSelectionTest {

    @Test
    fun freshSelectionNeedsNoApply() {
        assertFalse(PlaybackSelection().needsApply)
    }

    @Test
    fun newSelectionNeedsApplyUntilMarked() {
        val selection = PlaybackSelection()
        selection.set("a", listOf("a", "b"))
        assertTrue(selection.needsApply)
        selection.markApplied()
        assertFalse(selection.needsApply)
    }

    @Test
    fun reselectingAnotherItemInSameQueueNeedsApply() {
        val selection = PlaybackSelection()
        selection.set("a", listOf("a", "b"))
        selection.markApplied()
        selection.set("b", listOf("a", "b"))
        assertTrue("同一队列改成 b 也必须重新应用", selection.needsApply)
    }

    @Test
    fun reselectingTheSameItemNeedsApplyAgain() {
        val selection = PlaybackSelection()
        selection.set("a", listOf("a", "b"))
        selection.markApplied()
        selection.set("a", listOf("a", "b"))
        assertTrue(selection.needsApply)
    }

    @Test
    fun queueFallsBackToTheSelectedPath() {
        val selection = PlaybackSelection()
        selection.set("a", listOf("b"))
        assertEquals(listOf("a"), selection.queue)
        assertEquals("a", selection.path)
    }
}
