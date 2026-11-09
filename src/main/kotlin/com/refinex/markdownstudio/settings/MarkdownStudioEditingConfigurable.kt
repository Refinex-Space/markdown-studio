package com.refinex.markdownstudio.settings

import com.intellij.ide.DataManager
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.refinex.markdownstudio.MarkdownStudioBundle.message
import java.awt.Component

/** Settings | Tools | Markdown Studio | Editing: Live Preview's helpers and its shortcuts. */
class MarkdownStudioEditingConfigurable : StudioConfigurable("settings.editing.displayName", ID) {

    override fun createPanel(): DialogPanel = panel {
        val o = settings.state
        group(message("settings.group.livePreview")) {
            row {
                checkBox(message("settings.selectionToolbar")).bindSelected(o::selectionToolbar)
            }
        }
        group(message("settings.group.shortcuts")) {
            row {
                link(message("settings.keymap")) { e ->
                    // The Keymap page, filtered to Markdown Studio's actions.
                    val all = Settings.KEY.getData(DataManager.getInstance().getDataContext(e.source as Component))
                    all?.find(KEYMAP_ID)?.let { all.select(it, KEYMAP_FILTER) }
                }
                contextHelp(message("settings.keymap.help"))
            }
        }
    }

    companion object {
        const val ID = "com.refinex.markdownstudio.settings.editing"

        const val KEYMAP_ID = "preferences.keymap"

        /** The Keymap search that lists every plugin action: it matches their ids (`MarkdownStudio.*`). */
        const val KEYMAP_FILTER = "MarkdownStudio"
    }
}
