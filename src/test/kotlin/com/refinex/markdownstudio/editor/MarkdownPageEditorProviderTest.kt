package com.refinex.markdownstudio.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class MarkdownPageEditorProviderTest : BasePlatformTestCase() {

    private val provider = MarkdownPageEditorProvider()

    fun testPageStateRoundTripsWithoutTheFile() {
        for (live in listOf(true, false)) {
            val element = Element("provider")
            provider.writeState(MarkdownPageEditor.State(live), project, element)
            val read = provider.readState(element, project, lazy<VirtualFile> { error("the page state needs no file") })
            assertEquals(MarkdownPageEditor.State(live), read)
        }
    }

    /** 2026.3 deprecates `readState(Element, Project, VirtualFile)` and calls the `Lazy` overload instead. */
    fun testOnlyTheLazyReadStateIsImplemented() {
        val reads = MarkdownPageEditorProvider::class.java.declaredMethods.filter { it.name == "readState" }
        assertEquals(
            listOf(listOf(Element::class.java, Project::class.java, Lazy::class.java)),
            reads.map { it.parameterTypes.toList() },
        )
    }
}
