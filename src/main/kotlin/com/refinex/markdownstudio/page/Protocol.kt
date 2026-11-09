package com.refinex.markdownstudio.page

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Messages between the IDE and the editor page (`web/src/host/protocol.ts` is the other half):
 * JSON objects tagged by `type`.
 */
internal val PageJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
enum class PageMode {
    @SerialName("live") LIVE,
    @SerialName("preview") PREVIEW,
    @SerialName("split") SPLIT,
}

@Serializable
data class PageTheme(val scheme: String, val tokens: Map<String, String>)

@Serializable
data class PageSelection(val anchor: Int, val head: Int)

/**
 * Page options: Settings | Tools | Markdown Studio, and the IDE's own editor settings the page
 * follows (Editor | General: caret blinking; Smart Keys: pairing brackets and quotes).
 */
@Serializable
data class PagePrefs(
    val outline: Boolean,
    val selectionToolbar: Boolean,
    /** A full blink cycle in ms; 0 when the caret does not blink. */
    val caretBlink: Int = 1000,
    val pairBrackets: Boolean = true,
)

/** One change of the document: a CodeMirror `ChangeSet` in its JSON form, and who made it. */
@Serializable
data class WireUpdate(val changes: JsonArray, val clientID: String)

@Serializable
data class NativeMenuItem(
    val id: String,
    val kind: String,
    val label: String? = null,
    val icon: String? = null,
    val shortcut: String? = null,
    val disabled: Boolean = false,
    val checked: Boolean = false,
    val danger: Boolean = false,
    val action: String? = null,
    val children: List<NativeMenuItem>? = null,
)

@Serializable
sealed interface IdeMessage {
    @Serializable
    @SerialName("init")
    data class Init(
        val path: String,
        val text: String,
        val version: Int,
        val readOnly: Boolean,
        val mode: PageMode,
        val locale: String,
        val theme: PageTheme,
        val shortcuts: Map<String, String>,
        val remoteImages: Boolean,
        val prefs: PagePrefs,
        val selection: PageSelection? = null,
    ) : IdeMessage

    @Serializable
    @SerialName("updates")
    data class Updates(val version: Int, val updates: List<WireUpdate>) : IdeMessage

    @Serializable
    @SerialName("reset")
    data class Reset(val text: String, val version: Int) : IdeMessage

    @Serializable
    @SerialName("mode")
    data class Mode(val mode: PageMode) : IdeMessage

    @Serializable
    @SerialName("theme")
    data class Theme(val theme: PageTheme) : IdeMessage

    @Serializable
    @SerialName("shortcuts")
    data class Shortcuts(val shortcuts: Map<String, String>) : IdeMessage

    @Serializable
    @SerialName("readOnly")
    data class ReadOnly(val readOnly: Boolean) : IdeMessage

    @Serializable
    @SerialName("locale")
    data class Locale(val locale: String) : IdeMessage

    @Serializable
    @SerialName("prefs")
    data class Prefs(val prefs: PagePrefs) : IdeMessage

    @Serializable
    @SerialName("remoteImages")
    data class RemoteImages(val allowed: Boolean) : IdeMessage

    @Serializable
    @SerialName("scroll")
    data class Scroll(val topLine: Int) : IdeMessage

    @Serializable
    @SerialName("select")
    data class Select(val anchor: Int, val head: Int, val focus: Boolean) : IdeMessage

    @Serializable
    @SerialName("focus")
    data object Focus : IdeMessage

    @Serializable
    @SerialName("action")
    data class Action(val id: String, val arg: String? = null) : IdeMessage

    @Serializable
    @SerialName("menuInvoke")
    data class MenuInvoke(val menuId: Int, val itemId: String) : IdeMessage

    @Serializable
    @SerialName("response")
    data class Response(val id: Int, val ok: Boolean, val data: String? = null, val error: String? = null) : IdeMessage
}

@Serializable
sealed interface PageMessage {
    @Serializable
    @SerialName("ready")
    data object Ready : PageMessage

    /** The document is on screen (after `init`): the loading skeleton gives way. */
    @Serializable
    @SerialName("rendered")
    data object Rendered : PageMessage

    @Serializable
    @SerialName("resync")
    data object Resync : PageMessage

    @Serializable
    @SerialName("push")
    data class Push(val version: Int, val updates: List<WireUpdate>, val userEvent: String? = null) : PageMessage

    @Serializable
    @SerialName("selection")
    data class Selection(val anchor: Int, val head: Int) : PageMessage

    @Serializable
    @SerialName("focus")
    data class Focus(val focused: Boolean) : PageMessage

    /** The reader opened or closed the outline in the page. */
    @Serializable
    @SerialName("outline")
    data class Outline(val open: Boolean) : PageMessage

    @Serializable
    @SerialName("openFile")
    data class OpenFile(val path: String, val line: Int? = null, val col: Int? = null) : PageMessage

    @Serializable
    @SerialName("openExternal")
    data class OpenExternal(val url: String) : PageMessage

    @Serializable
    @SerialName("copyText")
    data class CopyText(val text: String) : PageMessage

    @Serializable
    @SerialName("contextMenu")
    data class ContextMenu(
        val menuId: Int,
        val x: Int,
        val y: Int,
        val items: List<NativeMenuItem>,
        val editor: Boolean = false,
    ) : PageMessage

    @Serializable
    @SerialName("request")
    data class Request(val id: Int, val method: String, val path: String) : PageMessage

    @Serializable
    @SerialName("log")
    data class Log(val level: String, val message: String) : PageMessage
}
