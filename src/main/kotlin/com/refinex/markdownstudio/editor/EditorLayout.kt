package com.refinex.markdownstudio.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import org.jetbrains.annotations.Nls
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.MarkdownStudioIcons
import com.refinex.markdownstudio.page.PageMode
import javax.swing.Icon

/**
 * The four layouts of a Markdown file: Live Preview (the page edits the rendered document),
 * Source (the IDE editor), Split (the IDE editor beside the page's preview) and Preview.
 */
enum class EditorLayout(val layout: Layout, val pageMode: PageMode?, private val key: String) {
    LIVE(Layout.SHOW_PREVIEW, PageMode.LIVE, "layout.live"),
    SOURCE(Layout.SHOW_EDITOR, null, "layout.source"),
    SPLIT(Layout.SHOW_EDITOR_AND_PREVIEW, PageMode.SPLIT, "layout.split"),
    PREVIEW(Layout.SHOW_PREVIEW, PageMode.PREVIEW, "layout.preview");

    val displayName: @Nls String get() = MarkdownStudioBundle.message(key)

    val icon: Icon
        get() = when (this) {
            LIVE -> MarkdownStudioIcons.LivePreview
            SOURCE -> AllIcons.General.LayoutEditorOnly
            SPLIT -> AllIcons.General.LayoutEditorPreview
            PREVIEW -> AllIcons.General.LayoutPreviewOnly
        }
}
