package com.refinex.markdownstudio

import com.intellij.openapi.vfs.VirtualFile

/** The files the page treats as Markdown (its `isMarkdownPath`, so Live Preview applies to them). */
object MarkdownFiles {

    private val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn")

    fun isMarkdown(file: VirtualFile): Boolean =
        !file.isDirectory && file.extension?.lowercase() in EXTENSIONS
}
