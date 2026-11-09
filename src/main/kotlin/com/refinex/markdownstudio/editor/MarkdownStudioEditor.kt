package com.refinex.markdownstudio.editor

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.page.PageSelection
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

/**
 * A Markdown file in Markdown Studio: the IDE's text editor (Source) and the editor page side by side
 * in the platform's split editor, switched between Live Preview, Source, Split and Preview.
 * Both edit the same document; switching layouts carries the caret across.
 */
class MarkdownStudioEditor(text: TextEditor, web: MarkdownPageEditor, initial: EditorLayout) :
    TextEditorWithPreview(text, web, MarkdownStudioBundle.message("editor.name"), initial.layout) {

    val web: MarkdownPageEditor get() = previewEditor as MarkdownPageEditor

    /** The layout last applied to the page (null until the editor is set up). */
    private var shown: EditorLayout? = null

    val editorLayout: EditorLayout
        get() = when (getLayout()) {
            Layout.SHOW_EDITOR_AND_PREVIEW -> EditorLayout.SPLIT
            Layout.SHOW_PREVIEW -> if (web.live) EditorLayout.LIVE else EditorLayout.PREVIEW
            Layout.SHOW_EDITOR, null -> EditorLayout.SOURCE
        }

    init {
        // The platform reopens with the layout last chosen anywhere; new files follow the setting.
        if (getLayout() != initial.layout) setLayout(initial.layout)
        web.live = initial != EditorLayout.PREVIEW
        web.page.initialSelection = sourceSelection()
        // Split: the page's preview follows the Source editor.
        editor.scrollingModel.addVisibleAreaListener({
            if (editorLayout == EditorLayout.SPLIT && MarkdownStudioSettings.getInstance().state.syncScrolling)
                web.page.scrollTo(sourceTopLine())
        }, this)
        apply(editorLayout)
    }

    fun setEditorLayout(target: EditorLayout) {
        MarkdownStudioSettings.getInstance().layoutChosen(target)
        if (target == editorLayout) return
        when (target) {
            EditorLayout.LIVE -> web.live = true
            EditorLayout.PREVIEW -> web.live = false
            else -> Unit
        }
        // Live Preview and Preview share the platform layout: no layout change to wait for.
        if (getLayout() != target.layout) setLayout(target.layout) else apply(target)
    }

    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
        super.onLayoutChange(oldValue, newValue)
        if (shown != null) apply(editorLayout)
    }

    /** Undo restores the text and the Source caret, not the layout the edit was made in. */
    override fun getState(level: FileEditorStateLevel): FileEditorState {
        val state = super.getState(level)
        if (level != FileEditorStateLevel.UNDO || state !is MyFileEditorState) return state
        return MyFileEditorState(null, state.firstState, null, state.isVerticalSplit)
    }

    override fun setState(state: FileEditorState) {
        super.setState(state)
        if (shown == null) return
        web.page.initialSelection = sourceSelection()
        apply(editorLayout)
    }

    override fun createViewActionGroup(): ActionGroup =
        ActionManager.getInstance().getAction("MarkdownStudio.Layouts") as ActionGroup

    private fun apply(target: EditorLayout) {
        val from = shown
        shown = target
        target.pageMode?.let { web.page.mode = it }
        if (from == null || from == target) return
        when {
            // Leaving Live Preview: the Source editor continues where the page's caret was.
            from == EditorLayout.LIVE && target.layout != Layout.SHOW_PREVIEW -> moveSourceCaret(web.page.selection)
            // Into Live Preview: the page continues from the Source editor's caret.
            target == EditorLayout.LIVE && from.layout != Layout.SHOW_PREVIEW ->
                sourceSelection().let { web.page.select(it.anchor, it.head, focus = true) }
            target == EditorLayout.LIVE -> web.page.focusEditor()
        }
        if (target == EditorLayout.SPLIT) web.page.scrollTo(sourceTopLine())
    }

    private fun sourceSelection(): PageSelection {
        val caret = editor.caretModel.primaryCaret
        return if (caret.hasSelection()) {
            val anchor = if (caret.offset == caret.selectionStart) caret.selectionEnd else caret.selectionStart
            PageSelection(anchor, caret.offset)
        } else {
            PageSelection(caret.offset, caret.offset)
        }
    }

    private fun moveSourceCaret(selection: PageSelection) {
        val length = editor.document.textLength
        val anchor = selection.anchor.coerceIn(0, length)
        val head = selection.head.coerceIn(0, length)
        editor.caretModel.moveToOffset(head)
        if (anchor != head) editor.selectionModel.setSelection(min(anchor, head), max(anchor, head))
        else editor.selectionModel.removeSelection()
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun sourceTopLine(): Int = editor.xyToLogicalPosition(Point(0, editor.scrollingModel.visibleArea.y)).line
}
