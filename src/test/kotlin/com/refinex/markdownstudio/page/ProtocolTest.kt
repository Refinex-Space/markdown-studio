package com.refinex.markdownstudio.page

import junit.framework.TestCase

class ProtocolTest : TestCase() {

    fun testIdeMessagesAreTaggedByType() {
        val json = PageJson.encodeToString(IdeMessage.serializer(), IdeMessage.Select(1, 2, focus = true))
        assertEquals("""{"type":"select","anchor":1,"head":2,"focus":true}""", json)
        assertEquals("""{"type":"focus"}""", PageJson.encodeToString(IdeMessage.serializer(), IdeMessage.Focus))
        assertEquals(
            """{"type":"prefs","prefs":{"outline":true,"selectionToolbar":false,"caretBlink":0,"pairBrackets":true}}""",
            PageJson.encodeToString(IdeMessage.serializer(), IdeMessage.Prefs(PagePrefs(true, false, 0, true))),
        )
    }

    fun testReadsPageMessagesAsThePageWritesThem() {
        val push = PageJson.decodeFromString(
            PageMessage.serializer(),
            """{"type":"push","version":3,"updates":[{"changes":[2,[0,"a","b"]],"clientID":"x1"}],"userEvent":"input.type"}""",
        ) as PageMessage.Push
        assertEquals(3, push.version)
        assertEquals("""[2,[0,"a","b"]]""", push.updates.single().changes.toString())

        val menu = PageJson.decodeFromString(
            PageMessage.serializer(),
            """{"type":"contextMenu","menuId":1,"x":10,"y":20,"editor":true,"items":[{"id":"0","kind":"item","label":"Format","children":[{"id":"0.0","kind":"item","label":"Bold","action":"markdown.bold"}]}]}""",
        ) as PageMessage.ContextMenu
        assertEquals("markdown.bold", menu.items.single().children!!.single().action)
        assertEquals(PageMessage.Ready, PageJson.decodeFromString(PageMessage.serializer(), """{"type":"ready"}"""))
        assertEquals(PageMessage.Outline(false), PageJson.decodeFromString(PageMessage.serializer(), """{"type":"outline","open":false}"""))
    }
}
