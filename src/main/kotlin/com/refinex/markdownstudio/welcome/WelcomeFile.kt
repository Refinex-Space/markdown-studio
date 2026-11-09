package com.refinex.markdownstudio.welcome

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.MarkdownStudioIcons
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

/** The welcome tab's file type: no extension maps to it, it only gives the tab its icon. */
object WelcomeFileType : FileType {
    override fun getName(): String = "MarkdownStudioWelcome"
    override fun getDescription(): String = MarkdownStudioBundle.message("welcome.tab")
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = MarkdownStudioIcons.Studio
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
}

/** The file behind the welcome tab (one per project, so reopening focuses the open tab). */
class WelcomeFile : LightVirtualFile(MarkdownStudioBundle.message("welcome.tab"), WelcomeFileType, "") {
    init {
        isWritable = false
    }
}

class WelcomeEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is WelcomeFile

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WelcomeEditor(project, file as WelcomeFile)

    override fun getEditorTypeId(): String = "markdown-studio-welcome"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class WelcomeEditor(project: Project, private val file: WelcomeFile) : UserDataHolderBase(), FileEditor {
    private val page = WelcomePage(project, this)

    override fun getComponent(): JComponent = page.component
    override fun getPreferredFocusedComponent(): JComponent = page.component
    override fun getName(): String = MarkdownStudioBundle.message("welcome.tab")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
}
