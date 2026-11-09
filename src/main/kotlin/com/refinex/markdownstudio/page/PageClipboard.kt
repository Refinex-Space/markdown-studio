package com.refinex.markdownstudio.page

import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext

/**
 * The IDE's Cut / Copy / Paste (their shortcuts and the editor menu) on the page: the browser's
 * own clipboard commands on the focused frame, so the page's editor handles them as typed ones.
 */
internal class PageClipboard(private val page: MarkdownPage) : CopyProvider, CutProvider, PasteProvider {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private val hasSelection get() = page.selection.let { it.anchor != it.head }
    private val editable get() = page.mode == PageMode.LIVE && !page.readOnly

    override fun performCopy(dataContext: DataContext) = page.frameCommand { copy() }
    override fun isCopyEnabled(dataContext: DataContext) = hasSelection
    override fun isCopyVisible(dataContext: DataContext) = true

    override fun performCut(dataContext: DataContext) = page.frameCommand { cut() }
    override fun isCutEnabled(dataContext: DataContext) = editable && hasSelection
    override fun isCutVisible(dataContext: DataContext) = true

    override fun performPaste(dataContext: DataContext) = page.frameCommand { paste() }
    override fun isPastePossible(dataContext: DataContext) = editable
    override fun isPasteEnabled(dataContext: DataContext) = editable
}
