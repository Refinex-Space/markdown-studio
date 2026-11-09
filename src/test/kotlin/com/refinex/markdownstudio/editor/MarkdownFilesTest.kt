package com.refinex.markdownstudio.editor

import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase
import com.refinex.markdownstudio.MarkdownFiles

class MarkdownFilesTest : TestCase() {

    fun testMarkdownExtensions() {
        assertTrue(MarkdownFiles.isMarkdown(LightVirtualFile("README.md")))
        assertTrue(MarkdownFiles.isMarkdown(LightVirtualFile("notes.MARKDOWN")))
        assertTrue(MarkdownFiles.isMarkdown(LightVirtualFile("a.mkdn")))
        assertFalse(MarkdownFiles.isMarkdown(LightVirtualFile("a.mdx")))
        assertFalse(MarkdownFiles.isMarkdown(LightVirtualFile("md")))
        assertFalse(MarkdownFiles.isMarkdown(LightVirtualFile("a.txt")))
    }
}
