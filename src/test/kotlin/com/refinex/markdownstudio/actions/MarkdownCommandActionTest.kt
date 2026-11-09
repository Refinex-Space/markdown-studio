package com.refinex.markdownstudio.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.refinex.markdownstudio.MarkdownStudioIcons

class MarkdownCommandActionTest : BasePlatformTestCase() {

    fun testActionIdsMapBothWays() {
        assertEquals("markdown.inlineCode", MarkdownCommandAction.commandId("MarkdownStudio.InlineCode"))
        assertEquals("markdown.heading1", MarkdownCommandAction.commandId("MarkdownStudio.Heading1"))
        assertEquals("MarkdownStudio.InsertTable", MarkdownCommandAction.ideActionId("markdown.insertTable"))
    }

    fun testPageEditorActionsFollowTheIdeEditorShortcuts() {
        assertEquals("editor.moveLineUp", PageEditorAction.pageActionId("MarkdownStudio.Page.MoveLineUp"))
        val manager = ActionManager.getInstance()
        val ids = manager.getActionIdList(PageEditorAction.PREFIX)
        assertEquals(17, ids.size)
        for (id in ids) {
            assertTrue(id, manager.getAction(id) is PageEditorAction)
            assertFalse("$id has no text", manager.getAction(id).templatePresentation.text.isNullOrBlank())
        }
        // `use-shortcut-of`: Duplicate Line's shortcut is also the page's.
        val duplicate = manager.getAction("MarkdownStudio.Page.Duplicate").shortcutSet.shortcuts.toList()
        assertEquals(manager.getAction("EditorDuplicate").shortcutSet.shortcuts.toList(), duplicate)
        val selectAll = manager.getAction("MarkdownStudio.Page.SelectAll").shortcutSet.shortcuts.toList()
        assertEquals(manager.getAction(IdeActions.ACTION_SELECT_ALL).shortcutSet.shortcuts.toList(), selectAll)
    }

    /** JCEF binds ⌘Z / ⌘A to the browser's own commands on macOS; in Live Preview the IDE's win. */
    fun testLivePreviewWinsUndoRedoAndSelectAll() {
        val manager = ActionManager.getInstance()
        val browserUndo = DumbAwareAction.create { }
        val undo = manager.getAction(IdeActions.ACTION_UNDO)
        val redo = manager.getAction(IdeActions.ACTION_REDO)
        val selectAll = manager.getAction("MarkdownStudio.Page.SelectAll")
        val ideSelectAll = manager.getAction(IdeActions.ACTION_SELECT_ALL)
        assertEquals(listOf(undo), MarkdownStudioActionPromoter.livePreviewFirst(listOf(browserUndo, undo)))
        assertEquals(listOf(redo), MarkdownStudioActionPromoter.livePreviewFirst(listOf(browserUndo, redo)))
        assertEquals(listOf(selectAll), MarkdownStudioActionPromoter.livePreviewFirst(listOf(browserUndo, ideSelectAll, selectAll)))
    }

    fun testEveryCommandIsRegisteredWithTextAndIcon() {
        val manager = ActionManager.getInstance()
        val commands = manager.getActionIdList(MarkdownCommandAction.PREFIX).filter { manager.getAction(it) is MarkdownCommandAction }
        assertEquals(26, commands.size)
        for (id in commands) {
            val presentation = manager.getAction(id).templatePresentation
            assertFalse("$id has no text", presentation.text.isNullOrBlank())
            assertNotNull("$id has no icon", presentation.icon)
        }
    }

    fun testPageIconNames() {
        assertNotNull(MarkdownStudioIcons.forName("md-heading-1"))
        assertNotNull(MarkdownStudioIcons.forName("md-bullet-list"))
        assertNotNull(MarkdownStudioIcons.forName("md-front-matter"))
        assertNotNull(MarkdownStudioIcons.forName("trash"))
        assertNull(MarkdownStudioIcons.forName("md-nothing"))
        assertNull(MarkdownStudioIcons.forName(null))
    }

    fun testDefaultShortcutsReachThePage() {
        val shortcuts = MarkdownCommandAction.shortcuts()
        assertTrue(shortcuts.toString(), shortcuts.keys.containsAll(listOf("markdown.bold", "markdown.italic", "markdown.strikethrough")))
    }
}
