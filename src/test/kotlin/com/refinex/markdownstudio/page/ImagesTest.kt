package com.refinex.markdownstudio.page

import junit.framework.TestCase
import java.nio.file.Files

class ImagesTest : TestCase() {

    fun testReadsImagesAsDataUrls() {
        val dir = Files.createTempDirectory("studio-images")
        val png = dir.resolve("a.PNG")
        Files.write(png, byteArrayOf(1, 2, 3))
        assertEquals("data:image/png;base64,AQID", MarkdownPage.readImage(png.toString()))
    }

    fun testReadsNothingButImages() {
        val dir = Files.createTempDirectory("studio-images")
        val text = dir.resolve("secret.txt")
        Files.writeString(text, "x")
        assertThrows(IllegalArgumentException::class.java) { MarkdownPage.readImage(text.toString()) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownPage.readImage(dir.resolve("missing.png").toString()) }
    }

    private fun assertThrows(type: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            assertTrue("expected ${type.simpleName}, got $e", type.isInstance(e))
            return
        }
        fail("expected ${type.simpleName}")
    }
}
