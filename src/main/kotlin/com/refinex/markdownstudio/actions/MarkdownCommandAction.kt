package com.refinex.markdownstudio.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.refinex.markdownstudio.editor.EditorLayout
import com.refinex.markdownstudio.editor.MarkdownStudioEditor
import javax.swing.SwingUtilities

/**
 * A Markdown command of the page's Live Preview (Bold, Heading 1, Insert Table…) as an IDE action,
 * so it is in Find Action, the keymap and menus. Registered once per command: the action id
 * names the command (`MarkdownStudio.InsertTable` runs the page's `markdown.insertTable`).
 */
class MarkdownCommandAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = liveEditor(e.dataContext)
        e.presentation.isEnabled = editor != null && !editor.web.page.readOnly
        localizePresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = liveEditor(e.dataContext) ?: return
        val id = ActionManager.getInstance().getId(this) ?: return
        editor.web.page.runAction(commandId(id))
    }

    companion object {
        const val PREFIX = "MarkdownStudio."

        /** `MarkdownStudio.InlineCode` → `markdown.inlineCode`. */
        fun commandId(actionId: String): String =
            "markdown." + actionId.removePrefix(PREFIX).replaceFirstChar { it.lowercaseChar() }

        /** `markdown.inlineCode` → `MarkdownStudio.InlineCode`. */
        fun ideActionId(commandId: String): String =
            PREFIX + commandId.removePrefix("markdown.").replaceFirstChar { it.uppercaseChar() }

        /** The editor in context while it shows Live Preview (the only layout these commands edit in). */
        fun liveEditor(context: DataContext): MarkdownStudioEditor? =
            (context.getData(PlatformCoreDataKeys.FILE_EDITOR) as? MarkdownStudioEditor)
                ?.takeIf { it.editorLayout == EditorLayout.LIVE }

        /**
         * The editor in context while its page shows the rendered preview to the keyboard: the
         * Preview layout, or Split with the focus in the preview beside the IDE editor.
         */
        fun previewEditor(context: DataContext): MarkdownStudioEditor? {
            val editor = context.getData(PlatformCoreDataKeys.FILE_EDITOR) as? MarkdownStudioEditor ?: return null
            return when (editor.editorLayout) {
                EditorLayout.PREVIEW -> editor
                EditorLayout.SPLIT -> editor.takeIf {
                    val focused = context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
                    focused != null && SwingUtilities.isDescendingFrom(focused, it.web.component)
                }
                else -> null
            }
        }

        /** Every command's first keyboard shortcut in the active keymap, by page command id (for the page's tooltips). */
        fun shortcuts(): Map<String, String> {
            val manager = ActionManager.getInstance()
            return manager.getActionIdList(PREFIX)
                .filter { manager.getAction(it) is MarkdownCommandAction }
                .mapNotNull { id -> KeymapUtil.getFirstKeyboardShortcutText(id).takeIf { it.isNotEmpty() }?.let { commandId(id) to it } }
                .toMap()
        }
    }
}

/**
 * An IDE editor action (Duplicate Line, Move Line Up, Extend Selection…) in the Live Preview
 * editor. Registered with `use-shortcut-of` the IDE action, so it follows the keymap;
 * `MarkdownStudio.Page.MoveLineUp` runs the page's `editor.moveLineUp`.
 */
class PageEditorAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val enabled = target(e.dataContext) != null
        e.presentation.isEnabledAndVisible = enabled
        if (enabled) localizePresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = target(e.dataContext) ?: return
        val id = ActionManager.getInstance().getId(this) ?: return
        editor.web.page.runAction(pageActionId(id))
    }

    /** Live Preview's editor; for Find, also the page's preview (it searches the rendered text). */
    private fun target(context: DataContext): MarkdownStudioEditor? =
        MarkdownCommandAction.liveEditor(context)
            ?: MarkdownCommandAction.previewEditor(context)?.takeIf { ActionManager.getInstance().getId(this) == FIND }

    companion object {
        const val PREFIX = "MarkdownStudio.Page."
        const val FIND = "MarkdownStudio.Page.Find"

        /** `MarkdownStudio.Page.MoveLineUp` → `editor.moveLineUp`. */
        fun pageActionId(actionId: String): String =
            "editor." + actionId.removePrefix(PREFIX).replaceFirstChar { it.lowercaseChar() }
    }
}

/**
 * While Live Preview edits, its actions win the shortcuts they share with others: ⌘B is Bold
 * there, not Go to Declaration; ⌘D duplicates the line rather than comparing files. Undo and Redo
 * come before the browser's own (JCEF binds them to the page on macOS): edits are IDE commands.
 * Over the preview, ⌘F opens the page's find bar rather than the hidden IDE editor's.
 */
class MarkdownStudioActionPromoter : ActionPromoter {
    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> = when {
        MarkdownCommandAction.liveEditor(context) != null -> livePreviewFirst(actions)
        MarkdownCommandAction.previewEditor(context) != null -> previewFirst(actions)
        else -> emptyList()
    }

    companion object {
        /** The action that wins its shortcut over the page's preview: Find searches the preview. */
        fun previewFirst(actions: List<AnAction>): List<AnAction> {
            val find = ActionManager.getInstance().getAction(PageEditorAction.FIND)
            return actions.filter { it === find }
        }

        /** The actions that win their shortcut while Live Preview edits, in the order given. */
        fun livePreviewFirst(actions: List<AnAction>): List<AnAction> {
            val manager = ActionManager.getInstance()
            val history = setOf(manager.getAction(IdeActions.ACTION_UNDO), manager.getAction(IdeActions.ACTION_REDO))
            return actions.filter { it is MarkdownCommandAction || it is PageEditorAction || it in history }
        }
    }
}
