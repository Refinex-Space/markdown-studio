package com.refinex.markdownstudio.page

import junit.framework.TestCase

class PageResourcesTest : TestCase() {

    fun testResourcePaths() {
        assertEquals("index.html", PageResources.resourcePath(PageResources.BASE_URL))
        assertEquals("index.html", PageResources.resourcePath(PageResources.BASE_URL + "index.html"))
        assertEquals("assets/index-x.js", PageResources.resourcePath(PageResources.BASE_URL + "assets/index-x.js?v=1"))
        assertNull(PageResources.resourcePath(PageResources.BASE_URL + "assets/../../secret"))
        assertNull(PageResources.resourcePath(PageResources.BASE_URL + "assets//x.js"))
    }

    fun testMimeTypes() {
        assertEquals("text/javascript", PageResources.mimeType("a/b.js"))
        assertEquals("font/woff2", PageResources.mimeType("f.WOFF2"))
        assertEquals("application/octet-stream", PageResources.mimeType("x.bin"))
    }

    fun testThePageIsPackaged() {
        val html = PageResources::class.java.getResourceAsStream("/web/index.html")?.use { it.readBytes().decodeToString() }
        assertNotNull("web build missing from the plugin resources", html)
        assertTrue("the IDE channel placeholder is kept by the build", html!!.contains("<!--studio-host-->"))
    }
}
