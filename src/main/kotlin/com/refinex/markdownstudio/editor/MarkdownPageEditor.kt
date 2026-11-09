package com.refinex.markdownstudio.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.page.MarkdownPage
import com.refinex.markdownstudio.page.PageMode
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** The page side of a Markdown file editor: the editor page, editing (Live) or showing a preview. */
class MarkdownPageEditor(project: Project, private val file: VirtualFile, initialLayout: EditorLayout) : UserDataHolderBase(), FileEditor {

    val page = MarkdownPage(
        project,
        file,
        FileDocumentManager.getInstance().getDocument(file) ?: error("no document for $file"),
        initialLayout.pageMode ?: PageMode.LIVE,
    )

    /** Which of the page's full-size layouts the editor shows: Live Preview, or the read-only preview. */
    var live: Boolean = initialLayout != EditorLayout.PREVIEW

    init {
        Disposer.register(this, page)
    }

    override fun getComponent(): JComponent = page.component

    override fun getPreferredFocusedComponent(): JComponent = page.focusComponent

    override fun getName(): String = MarkdownStudioBundle.message("editor.page.name")

    override fun getFile(): VirtualFile = file

    override fun getState(level: FileEditorStateLevel): FileEditorState = State(live)

    override fun setState(state: FileEditorState) {
        if (state is State) live = state.live
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun dispose() {}

    data class State(val live: Boolean) : FileEditorState {
        override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = otherState is State
    }
}
