package com.refinex.markdownstudio.page

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.refinex.markdownstudio.actions.MarkdownCommandAction
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.awt.datatransfer.StringSelection
import java.beans.PropertyChangeListener
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.swing.JComponent

/**
 * The Markdown editor page in a JCEF browser, kept in step with the file's [Document]: the
 * page edits through [DocumentAuthority], follows the IDE's look, keymap and language, and asks
 * the IDE to open files, links, menus and images.
 */
class MarkdownPage(
    private val project: Project,
    private val file: VirtualFile,
    document: Document,
    initialMode: PageMode,
) : Disposable {

    /** Off-screen rendering defaults to 30 frames a second; an editor needs typing and scrolling at 60. */
    private val browser: JBCefBrowser = JBCefBrowser.createBuilder().setWindowlessFramerate(FRAME_RATE).build()
    private val query: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val authority = DocumentAuthority(project, document, this, ::send) { offset ->
        if (mode == PageMode.LIVE) send(IdeMessage.Select(offset, offset, focus = false))
    }

    /** The page answered `ready` for the current load: messages reach it. */
    @Volatile
    private var ready = false

    @Volatile
    private var disposed = false

    @Volatile
    var mode: PageMode = initialMode
        set(value) {
            if (field == value) return
            field = value
            send(IdeMessage.Mode(value))
        }

    @Volatile
    var readOnly: Boolean = !document.isWritable
        private set

    /** The page editor's last selection (document offsets). */
    @Volatile
    var selection: PageSelection = PageSelection(0, 0)
        private set

    /** Where the page's editor starts (the Source editor's caret when the page is created). */
    var initialSelection: PageSelection? = null

    /** Covers the page until it has drawn the document (see [PageSkeleton]). */
    private val skeleton = PageSkeleton {
        val settings = MarkdownStudioSettings.getInstance().state
        PageSkeleton.Layout(
            columnWidth = settings.readingWidth.px,
            textSize = ThemeTokens.textSize(),
            live = mode == PageMode.LIVE,
            outline = settings.showOutline && mode != PageMode.SPLIT,
            zoom = zoom.toFloat(),
        )
    }

    val component: JComponent = UiDataProvider.wrapComponent(PageStack(browser.component, skeleton)) { sink ->
        val clipboard = PageClipboard(this)
        sink[PlatformDataKeys.COPY_PROVIDER] = clipboard
        sink[PlatformDataKeys.CUT_PROVIDER] = clipboard
        sink[PlatformDataKeys.PASTE_PROVIDER] = clipboard
    }

    /** The component that takes the keyboard: the browser's own (off-screen rendering), else its panel. */
    val focusComponent: JComponent get() = browser.browserComponent as? JComponent ?: browser.component

    init {
        Disposer.register(this, browser)
        Disposer.register(this, query)
        Disposer.register(this) { skeleton.stop() }
        skeleton.start()
        browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        browser.setPageBackgroundColor(ThemeTokens.css(EditorColorsManager.getInstance().globalScheme.defaultBackground))
        query.addHandler { json ->
            ApplicationManager.getApplication().invokeLater({ receive(json) }, ModalityState.nonModal()) { disposed }
            null
        }
        val client = browser.jbCefClient
        client.addRequestHandler(PageResources(this, ::hostScript, ::openExternal), browser.cefBrowser)
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                targetUrl?.let(::openExternal)
                return true
            }
        }, browser.cefBrowser)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                if (frame?.isMain != true) return
                ready = false
                // A reload (the first load included) shows the skeleton until the page has drawn again.
                ApplicationManager.getApplication().invokeLater({ if (!skeleton.isVisible) skeleton.start() }, ModalityState.any()) { disposed }
                // The out-of-process browser takes the rate once it exists (the builder's applies in-process only).
                if (this@MarkdownPage.browser.isOffScreenRendering) browser?.setWindowlessFrameRate(FRAME_RATE)
            }

            override fun onLoadError(browser: CefBrowser?, frame: CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                LOG.warn("Markdown Studio page failed to load $failedUrl: $errorCode $errorText")
            }
        }, browser.cefBrowser)
        val writable = PropertyChangeListener { e ->
            if (e.propertyName == Document.PROP_WRITABLE) {
                readOnly = !document.isWritable
                send(IdeMessage.ReadOnly(readOnly))
            }
        }
        document.addPropertyChangeListener(writable)
        Disposer.register(this) { document.removePropertyChangeListener(writable) }
        subscribe()
        updateZoom()
        browser.loadURL(PageResources.BASE_URL + PageResources.INDEX)
    }

    private fun subscribe() {
        val bus = ApplicationManager.getApplication().messageBus.connect(this)
        val theme = {
            // The browser's own background (behind the page, and the page's first frame on a reload).
            browser.setPageBackgroundColor(ThemeTokens.css(EditorColorsManager.getInstance().globalScheme.defaultBackground))
            send(IdeMessage.Theme(ThemeTokens.current()))
            send(IdeMessage.Prefs(prefs()))
        }
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { theme() })
        bus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { theme() })
        bus.subscribe(UISettingsListener.TOPIC, UISettingsListener {
            updateZoom()
            theme()
        })
        bus.subscribe(MarkdownStudioSettings.TOPIC, MarkdownStudioSettings.Listener {
            send(IdeMessage.Locale(pageLocale()))
            send(IdeMessage.Theme(ThemeTokens.current()))
            send(IdeMessage.Prefs(prefs()))
            send(IdeMessage.RemoteImages(MarkdownStudioSettings.getInstance().state.loadRemoteImages))
            send(IdeMessage.Shortcuts(MarkdownCommandAction.shortcuts()))
        })
        bus.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
            override fun activeKeymapChanged(keymap: Keymap?) = send(IdeMessage.Shortcuts(MarkdownCommandAction.shortcuts()))
            override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) =
                send(IdeMessage.Shortcuts(MarkdownCommandAction.shortcuts()))
        })
    }

    /**
     * Component pixels per page (CSS) pixel. JCEF already renders the page at the IDE zoom (it is part
     * of the browser's device scale), so the page is not zoomed again here; menus and the skeleton
     * use this to map page coordinates to the component's.
     */
    private var zoom = 1.0

    private fun updateZoom() {
        zoom = ThemeTokens.zoom()
    }

    private fun hostScript(): String =
        "<script>window.__studioPost = function (json) { ${query.inject("json")} };</script>"

    // --- IDE → page --------------------------------------------------------------------------

    private fun send(message: IdeMessage) {
        if (!ready || disposed) return
        val json = PageJson.encodeToString(IdeMessage.serializer(), message)
        browser.cefBrowser.executeJavaScript("window.__studioReceive && window.__studioReceive($json);", PageResources.BASE_URL, 0)
    }

    fun select(anchor: Int, head: Int, focus: Boolean) {
        selection = PageSelection(anchor, head)
        send(IdeMessage.Select(anchor, head, focus))
    }

    /** Split layout: the preview follows the Source editor's first visible line. */
    fun scrollTo(topLine: Int) = send(IdeMessage.Scroll(topLine))

    fun runAction(actionId: String, arg: String? = null) = send(IdeMessage.Action(actionId, arg))

    fun focusEditor() = send(IdeMessage.Focus)

    /** Runs a clipboard command of the browser on its focused frame. */
    internal fun frameCommand(command: CefFrame.() -> Unit) {
        browser.cefBrowser.focusedFrame?.command()
    }

    // --- page → IDE --------------------------------------------------------------------------

    private fun receive(json: String) {
        val message = try {
            PageJson.decodeFromString(PageMessage.serializer(), json)
        } catch (e: Exception) {
            LOG.warn("Markdown Studio: unreadable page message", e)
            return
        }
        when (message) {
            PageMessage.Ready -> init()
            PageMessage.Rendered -> skeleton.finish()
            PageMessage.Resync -> authority.snapshot().let { (text, version) -> send(IdeMessage.Reset(text, version)) }
            is PageMessage.Push -> authority.push(message)
            is PageMessage.Selection -> selection = PageSelection(message.anchor, message.head)
            is PageMessage.Focus -> Unit
            is PageMessage.Outline -> MarkdownStudioSettings.getInstance().let { settings ->
                if (settings.state.showOutline != message.open) {
                    settings.state.showOutline = message.open
                    settings.changed()
                }
            }
            is PageMessage.OpenFile -> openFile(message)
            is PageMessage.OpenExternal -> openExternal(message.url)
            is PageMessage.CopyText -> CopyPasteManager.getInstance().setContents(StringSelection(message.text))
            is PageMessage.ContextMenu -> showMenu(message)
            is PageMessage.Request -> request(message)
            is PageMessage.Log -> when (message.level) {
                "error" -> LOG.warn("page: ${message.message}")
                else -> LOG.info("page: ${message.message}")
            }
        }
    }

    private fun init() {
        ready = true
        val (text, version) = authority.snapshot()
        send(
            IdeMessage.Init(
                path = file.path,
                text = text,
                version = version,
                readOnly = readOnly,
                mode = mode,
                locale = pageLocale(),
                theme = ThemeTokens.current(),
                shortcuts = MarkdownCommandAction.shortcuts(),
                remoteImages = MarkdownStudioSettings.getInstance().state.loadRemoteImages,
                prefs = prefs(),
                selection = initialSelection ?: selection,
            ),
        )
    }

    private fun pageLocale(): String = MarkdownStudioSettings.getInstance().locale.toLanguageTag()

    private fun prefs(): PagePrefs {
        val o = MarkdownStudioSettings.getInstance().state
        val editor = EditorSettingsExternalizable.getInstance()
        return PagePrefs(
            outline = o.showOutline,
            selectionToolbar = o.selectionToolbar,
            caretBlink = if (editor.isBlinkCaret) editor.blinkPeriod * 2 else 0,
            pairBrackets = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET,
        )
    }

    private fun openFile(message: PageMessage.OpenFile) {
        val target = LocalFileSystem.getInstance().refreshAndFindFileByPath(message.path) ?: return
        OpenFileDescriptor(project, target, message.line ?: -1, message.col ?: -1).navigate(true)
    }

    private fun openExternal(url: String) {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) BrowserUtil.browse(url)
    }

    private fun showMenu(menu: PageMessage.ContextMenu) {
        if (!browser.component.isShowing) return
        PageMenus.show(menu, browser.component, (menu.x * zoom).toInt(), (menu.y * zoom).toInt()) { menuId, itemId ->
            send(IdeMessage.MenuInvoke(menuId, itemId))
        }
    }

    private fun request(request: PageMessage.Request) {
        if (request.method != "readImage") {
            send(IdeMessage.Response(request.id, ok = false, error = "unknown method ${request.method}"))
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val response = try {
                IdeMessage.Response(request.id, ok = true, data = readImage(request.path))
            } catch (e: Exception) {
                IdeMessage.Response(request.id, ok = false, error = e.message ?: e.javaClass.simpleName)
            }
            send(response)
        }
    }

    override fun dispose() {
        disposed = true
        ready = false
    }

    companion object {
        private val LOG = logger<MarkdownPage>()

        private const val FRAME_RATE = 60

        private val IMAGE_TYPES = mapOf(
            "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
            "webp" to "image/webp", "svg" to "image/svg+xml", "ico" to "image/x-icon", "bmp" to "image/bmp",
            "avif" to "image/avif",
        )
        private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

        /** A local image as a data URL; only image files, and not huge ones. */
        internal fun readImage(path: String): String {
            val file = Path.of(path)
            val type = IMAGE_TYPES[file.fileName.toString().substringAfterLast('.', "").lowercase()]
                ?: throw IllegalArgumentException("not an image: $path")
            require(Files.isRegularFile(file)) { "no such file: $path" }
            require(Files.size(file) <= MAX_IMAGE_BYTES) { "image too large: $path" }
            return "data:$type;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file))
        }
    }
}
