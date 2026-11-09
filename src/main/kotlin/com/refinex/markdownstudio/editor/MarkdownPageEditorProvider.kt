package com.refinex.markdownstudio.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import org.jdom.Element
import com.refinex.markdownstudio.MarkdownFiles
import com.refinex.markdownstudio.settings.MarkdownStudioSettings

/** Creates the page side of [MarkdownStudioEditor]; not registered on its own. */
class MarkdownPageEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        MarkdownStudioSettings.getInstance().state.enabled && MarkdownFiles.isMarkdown(file) && JBCefApp.isSupported()

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        MarkdownPageEditor(project, file, MarkdownStudioSettings.getInstance().initialLayout)

    override fun getEditorTypeId(): String = "markdown-studio-page"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    /**
     * Overrides `FileEditorProvider.readState(Element, Project, Lazy<VirtualFile>)`, which 2026.3 added in place of
     * the deprecated `VirtualFile` overload. Built against 2026.2, where the platform has no such method, so it is
     * declared without `override`; it needs one once the build targets 2026.3. 2026.2 restores the default layout.
     */
    fun readState(sourceElement: Element, project: Project, file: Lazy<VirtualFile>): FileEditorState =
        MarkdownPageEditor.State(sourceElement.getAttributeValue(LIVE) != "false")

    override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
        if (state is MarkdownPageEditor.State) targetElement.setAttribute(LIVE, state.live.toString())
    }

    private companion object {
        const val LIVE = "live"
    }
}
