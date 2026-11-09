package com.refinex.markdownstudio.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.refinex.markdownstudio.MarkdownStudioBundle
import javax.swing.JComponent

/**
 * A settings page built with the Kotlin UI DSL, rebuilt each time the page opens so it shows
 * Markdown Studio's current language; applying notifies open editors.
 */
abstract class StudioConfigurable(private val nameKey: String, private val id: String) : SearchableConfigurable {

    protected val settings: MarkdownStudioSettings = MarkdownStudioSettings.getInstance()

    private var panel: DialogPanel? = null

    protected abstract fun createPanel(): DialogPanel

    override fun getId(): String = id

    override fun getDisplayName(): String = MarkdownStudioBundle.message(nameKey)

    override fun createComponent(): JComponent = createPanel().also { panel = it }

    override fun isModified(): Boolean = panel?.isModified() == true

    override fun apply() {
        panel?.apply()
        settings.changed()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
