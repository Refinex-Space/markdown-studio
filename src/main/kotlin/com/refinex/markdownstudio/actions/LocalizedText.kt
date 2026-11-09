package com.refinex.markdownstudio.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import com.refinex.markdownstudio.settings.StudioLanguage

/**
 * The action's text and description in the language chosen in Settings | Tools | Markdown Studio
 * (plugin.xml texts follow the IDE's language; menus and Find Action show these instead).
 */
fun AnAction.localizePresentation(e: AnActionEvent) {
    if (MarkdownStudioSettings.getInstance().state.language == StudioLanguage.IDE) return
    val id = ActionManager.getInstance().getId(this) ?: return
    e.presentation.text = MarkdownStudioBundle.message("action.$id.text")
    e.presentation.description = MarkdownStudioBundle.message("action.$id.description")
}
