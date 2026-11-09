package com.refinex.markdownstudio.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareToggleAction
import com.refinex.markdownstudio.actions.localizePresentation
import com.refinex.markdownstudio.settings.MarkdownStudioSettings

/** Switches the Markdown editor in context to one layout (the editor toolbar, Find Action, keymap). */
abstract class LayoutAction(private val target: EditorLayout) : DumbAwareToggleAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = editor(e) != null
        localizePresentation(e)
    }

    override fun isSelected(e: AnActionEvent): Boolean = editor(e)?.editorLayout == target

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) editor(e)?.setEditorLayout(target)
    }

    private fun editor(e: AnActionEvent): MarkdownStudioEditor? = e.getData(PlatformCoreDataKeys.FILE_EDITOR) as? MarkdownStudioEditor
}

class LiveLayoutAction : LayoutAction(EditorLayout.LIVE)

class SourceLayoutAction : LayoutAction(EditorLayout.SOURCE)

class SplitLayoutAction : LayoutAction(EditorLayout.SPLIT)

class PreviewLayoutAction : LayoutAction(EditorLayout.PREVIEW)

/** The document outline beside the page (Live Preview, Preview, Split); one setting for every file. */
class ToggleOutlineAction : DumbAwareToggleAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val editor = e.getData(PlatformCoreDataKeys.FILE_EDITOR) as? MarkdownStudioEditor
        e.presentation.isEnabledAndVisible = editor != null && editor.editorLayout != EditorLayout.SOURCE
        localizePresentation(e)
    }

    override fun isSelected(e: AnActionEvent): Boolean = MarkdownStudioSettings.getInstance().state.showOutline

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val settings = MarkdownStudioSettings.getInstance()
        settings.state.showOutline = state
        settings.changed()
    }
}
