package com.refinex.markdownstudio.page

import com.intellij.openapi.Disposable
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

/**
 * Serves a page from the plugin (a directory of the jar: `/web` for the editor page) at
 * [BASE_URL], and keeps the browser on it: any other navigation goes to [openExternal] instead.
 */
internal class PageResources(
    private val parent: Disposable,
    /** The page's `<!--studio-host-->` placeholder becomes this script (the IDE channel). */
    private val hostScript: () -> String,
    private val openExternal: (String) -> Unit,
    /** The jar directory the page's files are in. */
    private val root: String = "web",
) : CefRequestHandlerAdapter() {

    override fun onBeforeBrowse(browser: CefBrowser?, frame: CefFrame?, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
        if (request.url.startsWith(BASE_URL)) return false
        if (userGesture) openExternal(request.url)
        return true
    }

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler? {
        if (!request.url.startsWith(BASE_URL)) return null
        return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler? {
                val path = resourcePath(request.url) ?: return null
                val stream = open(path) ?: return null
                return JBCefStreamResourceHandler(stream, mimeType(path), parent, HEADERS)
            }
        }
    }

    private fun open(path: String): InputStream? {
        val stream = PageResources::class.java.getResourceAsStream("/$root/$path") ?: return null
        if (path != INDEX) return stream
        val html = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return ByteArrayInputStream(html.replace(HOST_PLACEHOLDER, hostScript()).toByteArray(Charsets.UTF_8))
    }

    companion object {
        /** `.localhost` never leaves the machine and is a secure context; the requests never reach a network. */
        const val BASE_URL = "https://markdown-studio.localhost/"
        const val INDEX = "index.html"
        private const val HOST_PLACEHOLDER = "<!--studio-host-->"
        private val HEADERS = mapOf("Cache-Control" to "no-store")

        /** The resource path of a page URL, or null for anything outside the page's files. */
        fun resourcePath(url: String): String? {
            val path = runCatching { URI(url).path }.getOrNull()?.removePrefix("/") ?: return null
            val normalized = path.ifEmpty { INDEX }
            if (normalized.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
            return normalized
        }

        fun mimeType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "woff2" -> "font/woff2"
            "woff" -> "font/woff"
            "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
