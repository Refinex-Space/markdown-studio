package com.refinex.markdownstudio.page

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.MarkdownStudioIcons
import com.refinex.markdownstudio.actions.MarkdownCommandAction
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import com.refinex.markdownstudio.settings.StudioLanguage
import java.awt.Component

/**
 * The page's context menus as IDE popup menus. The editor menu starts with the IDE's own
 * Cut / Copy / Paste (backed by the page, see [PageClipboard]); the page's items follow, and the
 * chosen one runs in the page.
 */
internal object PageMenus {

    const val PLACE = "MarkdownStudioPage"

    private val EDIT_ACTIONS = listOf(
        IdeActions.ACTION_CUT to "page.menu.cut",
        IdeActions.ACTION_COPY to "page.menu.copy",
        IdeActions.ACTION_PASTE to "page.menu.paste",
    )

    fun show(menu: PageMessage.ContextMenu, invoker: Component, x: Int, y: Int, invoke: (menuId: Int, itemId: String) -> Unit) {
        val group = DefaultActionGroup()
        val manager = ActionManager.getInstance()
        if (menu.editor) {
            EDIT_ACTIONS.forEach { (id, key) -> group.add(localized(manager.getAction(id), key)) }
            if (menu.items.isNotEmpty()) group.addSeparator()
        }
        addItems(group, menu.items) { invoke(menu.menuId, it) }
        manager.createActionPopupMenu(PLACE, group).component.show(invoker, x, y)
    }

    /** The IDE's own action, titled in Markdown Studio's language when one is chosen (the menu reads as one). */
    private fun localized(action: AnAction, key: String): AnAction {
        if (MarkdownStudioSettings.getInstance().state.language == StudioLanguage.IDE) return action
        return object : AnActionWrapper(action) {
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.text = MarkdownStudioBundle.message(key)
            }
        }
    }

    private fun addItems(group: DefaultActionGroup, items: List<NativeMenuItem>, invoke: (String) -> Unit) {
        for (item in items) {
            when {
                item.kind == "separator" -> group.add(Separator.getInstance())
                item.kind == "header" -> group.add(Separator(item.label))
                item.children != null -> {
                    val sub = DefaultActionGroup(item.label.orEmpty(), true)
                    sub.templatePresentation.icon = MarkdownStudioIcons.forName(item.icon)
                    addItems(sub, item.children, invoke)
                    group.add(sub)
                }
                else -> group.add(itemAction(item, invoke))
            }
        }
    }

    private fun itemAction(item: NativeMenuItem, invoke: (String) -> Unit): AnAction {
        val icon = MarkdownStudioIcons.forName(item.icon)
        val action: AnAction = if (item.checked) {
            object : DumbAwareToggleAction(item.label, null, icon) {
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
                override fun isSelected(e: AnActionEvent) = true
                override fun setSelected(e: AnActionEvent, state: Boolean) = invoke(item.id)
                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.isEnabled = !item.disabled
                }
            }
        } else {
            object : DumbAwareAction(item.label, null, icon) {
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !item.disabled
                }
                override fun actionPerformed(e: AnActionEvent) = invoke(item.id)
            }
        }
        // A page action shows its IDE counterpart's shortcut.
        item.action?.let { ActionManager.getInstance().getAction(MarkdownCommandAction.ideActionId(it))?.let(action::copyShortcutFrom) }
        return action
    }
}
