package com.refinex.markdownstudio.page

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.JsonArray
import kotlin.random.Random

class ChangeSetsTest : BasePlatformTestCase() {

    fun testReplacementJson() {
        assertEquals("""[2,[3,"x","y"],4]""", ChangeSets.replacement(9, 2, 3, "x\ny").toString())
        assertEquals("""[[0,"a"],5]""", ChangeSets.replacement(5, 0, 0, "a").toString())
        assertEquals("""[4,[1]]""", ChangeSets.replacement(5, 4, 1, "").toString())
    }

    fun testLengths() {
        val changes = ChangeSets.replacement(9, 2, 3, "x\ny")
        assertEquals(9, ChangeSets.lengthBefore(changes))
        assertEquals(9 - 3 + 3, ChangeSets.lengthAfter(changes))
    }

    /** Document events become change sets that replay the same edits on a copy. */
    fun testEventsReplayOnACopy() {
        val random = Random(42)
        val source = DocumentImpl("# Title\n\nSome *text* here.\n- a\n- b\n")
        val copy = DocumentImpl(source.text)
        val captured = ArrayList<JsonArray>()
        source.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                captured += ChangeSets.fromEvent(event)
            }
        })
        WriteCommandAction.runWriteCommandAction(project) {
            repeat(300) {
                val length = source.textLength
                val from = random.nextInt(length + 1)
                val to = (from + random.nextInt(4)).coerceAtMost(length)
                val text = listOf("", "x", "\n", "中文", "**b**\n- ").random(random)
                if (from == to && text.isEmpty()) return@repeat
                val events = captured.size
                source.replaceString(from, to, text)
                // Replacing text by itself is no change (and no event).
                if (captured.size > events) ChangeSets.apply(copy, captured.last())
                assertEquals(source.text, copy.text)
            }
        }
    }

    fun testApplyRejectsAChangeSetForAnotherLength() {
        val document = DocumentImpl("abc")
        assertThrows(IllegalArgumentException::class.java) { ChangeSets.apply(document, ChangeSets.replacement(4, 0, 1, "x")) }
        assertEquals("abc", document.text)
    }
}
