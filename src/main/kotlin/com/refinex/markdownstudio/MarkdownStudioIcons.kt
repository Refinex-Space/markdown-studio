package com.refinex.markdownstudio

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** The Markdown icons (`/icons/markdown`, built from the page's SVGs in `web/core`) and the page's icon names. */
object MarkdownStudioIcons {

    @JvmField
    val LivePreview: Icon = load("livePreview")

    @JvmField
    val Outline: Icon = load("outline")

    /** The plugin's own icon at 16 px (the welcome tab). */
    @JvmField
    val Studio: Icon = IconLoader.getIcon("/icons/markdownStudio.svg", MarkdownStudioIcons::class.java)

    private fun load(name: String): Icon = IconLoader.getIcon("/icons/markdown/$name.svg", MarkdownStudioIcons::class.java)

    private val GENERAL: Map<String, Icon> = mapOf(
        "copy" to AllIcons.Actions.Copy,
        "paste" to AllIcons.Actions.MenuPaste,
        "trash" to AllIcons.Actions.GC,
        "maximize" to AllIcons.General.ExpandComponent,
        "close" to AllIcons.Actions.Close,
        "check" to AllIcons.Actions.Checked,
    )

    /**
     * The icon behind a page icon name: `md-heading-1` is the page's `luma/markdown/heading1.svg`,
     * `md-bullet-list` its `bulletList.svg`; a few general names map to the platform's icons.
     */
    fun forName(name: String?): Icon? {
        if (name == null) return null
        GENERAL[name]?.let { return it }
        if (!name.startsWith("md-")) return null
        val file = name.removePrefix("md-").split('-').mapIndexed { i, part ->
            if (i == 0) part else part.replaceFirstChar { it.uppercaseChar() }
        }.joinToString("")
        return if (MarkdownStudioIcons::class.java.getResource("/icons/markdown/$file.svg") != null) load(file) else null
    }
}
