package com.refinex.markdownstudio.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.refinex.markdownstudio.settings.MarkdownStudioSettings

/**
 * Opens Markdown files in [MarkdownStudioEditor], in place of the IDE's own Markdown editors
 * (Settings | Tools | Markdown Studio turns it off). Without JCEF the IDE's editors stay.
 */
class MarkdownStudioEditorProvider : TextEditorWithPreviewProvider(MarkdownPageEditorProvider()), DumbAware {

    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor =
        MarkdownStudioEditor(firstEditor, secondEditor as MarkdownPageEditor, MarkdownStudioSettings.getInstance().initialLayout)

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
