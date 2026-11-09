package com.refinex.markdownstudio.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.ColoredListCellRenderer
import javax.swing.JList
import com.refinex.markdownstudio.MarkdownFiles
import com.refinex.markdownstudio.MarkdownStudioBundle.message
import com.refinex.markdownstudio.editor.EditorLayout
import java.util.Locale

/** Settings | Tools | Markdown Studio: which editor opens Markdown files, in which layout and language. */
class MarkdownStudioConfigurable : StudioConfigurable("settings.displayName", ID) {

    override fun createPanel(): DialogPanel = panel {
        val o = settings.state
        row {
            checkBox(message("settings.enabled")).bindSelected(o::enabled)
            contextHelp(message("settings.enabled.help"))
        }
        group(message("settings.group.layout")) {
            row(message("settings.defaultLayout")) {
                comboBox(listOf(null) + EditorLayout.entries, LayoutRenderer()).bindItem(o::defaultLayout)
            }
            row {
                checkBox(message("settings.syncScrolling")).bindSelected(o::syncScrolling)
            }
        }
        group(message("settings.group.language")) {
            row(message("settings.language")) {
                comboBox(StudioLanguage.entries, LanguageRenderer())
                    .bindItem({ o.language }, { o.language = it ?: StudioLanguage.IDE })
                contextHelp(message("settings.language.help"))
            }
        }
    }

    override fun apply() {
        val wasEnabled = settings.state.enabled
        super.apply()
        if (wasEnabled != settings.state.enabled) reopenMarkdownFiles()
    }

    /** Open Markdown files switch editors now rather than on their next opening. */
    private fun reopenMarkdownFiles() {
        for (project in ProjectManager.getInstance().openProjects) {
            val manager = FileEditorManagerEx.getInstanceEx(project)
            for (file in manager.openFiles.filter(MarkdownFiles::isMarkdown)) {
                val wasSelected = file in manager.selectedFiles
                manager.closeFile(file)
                manager.openFile(file, wasSelected)
            }
        }
    }

    /** A layout with its toolbar icon; `null` is "Last used layout". */
    private class LayoutRenderer : ColoredListCellRenderer<EditorLayout?>() {
        override fun customizeCellRenderer(list: JList<out EditorLayout?>, value: EditorLayout?, index: Int, selected: Boolean, hasFocus: Boolean) {
            icon = value?.icon ?: AllIcons.Vcs.History
            append(value?.displayName ?: message("settings.defaultLayout.lastUsed"))
        }
    }

    private class LanguageRenderer : ColoredListCellRenderer<StudioLanguage>() {
        override fun customizeCellRenderer(list: JList<out StudioLanguage>, value: StudioLanguage?, index: Int, selected: Boolean, hasFocus: Boolean) {
            value?.let { append(languageName(it)) }
        }
    }

    companion object {
        const val ID = "com.refinex.markdownstudio.settings"

        /** Each language in its own name; "Same as the IDE" says which one that is now. */
        fun languageName(language: StudioLanguage): String = when (language) {
            StudioLanguage.IDE -> message("settings.language.ide", nativeName(language.effectiveLocale))
            else -> nativeName(language.effectiveLocale)
        }

        private fun nativeName(locale: Locale) = if (locale.language == Locale.CHINESE.language) "简体中文" else "English"
    }
}
