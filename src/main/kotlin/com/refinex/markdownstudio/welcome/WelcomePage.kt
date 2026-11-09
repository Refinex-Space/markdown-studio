package com.refinex.markdownstudio.welcome

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.editor.EditorLayout
import com.refinex.markdownstudio.page.PageResources
import com.refinex.markdownstudio.page.ThemeTokens
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import javax.swing.JComponent

/**
 * The welcome page in a JCEF browser (`/welcome` in the jar): what the plugin does, the layout new
 * files open in, the tour and the keys. Its text and colours come from the IDE (language setting,
 * theme, keymap) and follow them while the tab is open.
 */
internal class WelcomePage(private val project: Project, parent: Disposable) {

    private val browser = JBCefBrowser.createBuilder().build()
    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, browser)
        Disposer.register(parent, query)
        browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        browser.setPageBackgroundColor(ThemeTokens.css(EditorColorsManager.getInstance().globalScheme.defaultBackground))
        query.addHandler { json ->
            ApplicationManager.getApplication().invokeLater({ receive(json) }, ModalityState.nonModal(), project.disposed)
            null
        }
        val client = browser.jbCefClient
        client.addRequestHandler(PageResources(parent, ::hostScript, ::openLink, root = "welcome"), browser.cefBrowser)
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                targetUrl?.let(::openLink)
                return true
            }
        }, browser.cefBrowser)
        subscribe(parent)
        browser.loadURL(PageResources.BASE_URL + PageResources.INDEX)
    }

    private fun subscribe(parent: Disposable) {
        val bus = ApplicationManager.getApplication().messageBus.connect(parent)
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { render() })
        bus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { render() })
        bus.subscribe(UISettingsListener.TOPIC, UISettingsListener { render() })
        bus.subscribe(MarkdownStudioSettings.TOPIC, MarkdownStudioSettings.Listener { render() })
        bus.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
            override fun activeKeymapChanged(keymap: Keymap?) = render()
            override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) = render()
        })
    }

    /** The IDE channel and the page's first data, before the page's own script runs. */
    private fun hostScript(): String =
        "<script>window.__studioPost = function (json) { ${query.inject("json")} }; window.__welcome = ${data()};</script>"

    private fun render() {
        browser.setPageBackgroundColor(ThemeTokens.css(EditorColorsManager.getInstance().globalScheme.defaultBackground))
        browser.cefBrowser.executeJavaScript("window.__welcomeRender && window.__welcomeRender(${data()});", PageResources.BASE_URL, 0)
    }

    private fun receive(json: String) {
        val message = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        when (message["type"]?.jsonPrimitive?.content) {
            "tour" -> Welcome.openTour(project)
            "settings" -> Welcome.openSettings(project)
            "keymap" -> Welcome.openKeymap(project)
            "layout" -> message["id"]?.jsonPrimitive?.content?.let { id ->
                val layout = EditorLayout.entries.firstOrNull { it.name == id } ?: return
                val settings = MarkdownStudioSettings.getInstance()
                settings.state.defaultLayout = layout
                settings.changed()
            }
        }
    }

    private fun openLink(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) BrowserUtil.browse(url)
    }

    /** Everything the page shows, as JSON safe to place in a `<script>` element. */
    private fun data(): String = Json.encodeToString(WelcomeData.serializer(), WelcomeData.current()).replace("</", "<\\/")
}

@Serializable
internal data class WelcomeData(
    val locale: String,
    val scheme: String,
    val tokens: Map<String, String>,
    val version: String,
    val logo: String,
    /** The layout new files open in, or null for the one used last. */
    val layout: String?,
    val text: Map<String, String>,
    val layouts: List<Item>,
    val features: List<Item>,
    val keys: List<Key>,
) {
    @Serializable
    data class Item(val id: String, val title: String, val body: String = "", val icon: String = "")

    @Serializable
    data class Key(val label: String, val keys: String)

    companion object {
        private val TEXT = listOf(
            "title", "tagline", "tour", "settings", "layouts", "layouts.default", "features", "keys", "keymap",
        )
        private val FEATURES = listOf(
            "live" to "livePreview",
            "slash" to "text",
            "tables" to "table",
            "blocks" to "diagram",
            "outline" to "outline",
            "ide" to "sourceCode",
        )
        /** Keys worth knowing on day one: Markdown commands and the IDE's editor actions in the page. */
        private val KEYS = listOf(
            "bold" to "MarkdownStudio.Bold",
            "italic" to "MarkdownStudio.Italic",
            "strike" to "MarkdownStudio.Strikethrough",
            "find" to "MarkdownStudio.Page.Find",
            "selectBlock" to "MarkdownStudio.Page.SelectAll",
            "extend" to "MarkdownStudio.Page.ExtendSelection",
            "duplicate" to "MarkdownStudio.Page.Duplicate",
        )

        fun current(): WelcomeData {
            val settings = MarkdownStudioSettings.getInstance()
            val theme = ThemeTokens.current()
            return WelcomeData(
                locale = settings.locale.toLanguageTag(),
                scheme = theme.scheme,
                tokens = theme.tokens,
                version = Welcome.version(),
                logo = resource("/META-INF/pluginIcon.svg"),
                layout = settings.state.defaultLayout?.name,
                text = TEXT.associateWith { MarkdownStudioBundle.message("welcome.$it") },
                layouts = EditorLayout.entries.map { Item(it.name, it.displayName, MarkdownStudioBundle.message("welcome.layout.${it.name.lowercase()}")) },
                features = FEATURES.map { (id, icon) ->
                    Item(id, MarkdownStudioBundle.message("welcome.feature.$id"), MarkdownStudioBundle.message("welcome.feature.$id.body"), icon(icon))
                },
                keys = listOf(Key(MarkdownStudioBundle.message("welcome.key.slash"), "/")) + KEYS.mapNotNull { (id, action) ->
                    KeymapUtil.getFirstKeyboardShortcutText(action).takeIf { it.isNotEmpty() }?.let { Key(MarkdownStudioBundle.message("welcome.key.$id"), it) }
                },
            )
        }

        private fun resource(path: String): String =
            WelcomeData::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

        /** A Markdown icon drawn in the text colour (the light variant's grey becomes `currentColor`). */
        private fun icon(name: String): String = resource("/icons/markdown/$name.svg").replace("#6C707E", "currentColor")
    }
}
