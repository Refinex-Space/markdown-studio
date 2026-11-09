package com.refinex.markdownstudio.page

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLayeredPane
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the page area shows while the page loads: the shape of a document (title, paragraphs, a
 * code block, a list, the outline) in quiet bars of the editor's colours, a soft light sweeping
 * across them. It sits where the page's text will appear and fades out once the page reports the
 * document on screen, so opening a file goes from its outline to its text without a blank panel.
 */
internal class PageSkeleton(private val shapeOf: () -> Layout) : JComponent() {

    /** Where the page will put its text, in the page's CSS pixels (scaled by [zoom] to the component's). */
    data class Layout(
        /** The reading column's width, or null for the full width. */
        val columnWidth: Int?,
        /** The document's text size. */
        val textSize: Float,
        /** Live Preview's editor (true) or the rendered preview (false), which pads its column more. */
        val live: Boolean,
        val outline: Boolean,
        val zoom: Float,
    )

    private val timer = Timer(FRAME_MS) { tick() }
    private var started = 0L
    private var fadeStarted = 0L
    private var alpha = 1f

    init {
        isOpaque = false
        isFocusable = false
        isVisible = false
    }

    /** Shows the skeleton (opening the file, or the page reloading). */
    fun start() {
        started = System.currentTimeMillis()
        fadeStarted = 0L
        alpha = 1f
        isVisible = true
        timer.start()
        repaint()
    }

    /** The page has drawn the document: fade out. */
    fun finish() {
        if (!isVisible || fadeStarted != 0L) return
        fadeStarted = System.currentTimeMillis()
    }

    fun stop() {
        timer.stop()
        isVisible = false
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (fadeStarted == 0L && now - started > GIVE_UP_MS) finish()
        if (fadeStarted != 0L) {
            val t = (now - fadeStarted) / FADE_MS.toFloat()
            if (t >= 1f) return stop()
            alpha = 1f - easeOut(t)
        }
        if (isShowing) repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.composite = AlphaComposite.SrcOver.derive(alpha)
            paintSkeleton(g, shapeOf())
        } finally {
            g.dispose()
        }
    }

    private fun paintSkeleton(g: Graphics2D, l: Layout) {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val bg = scheme.defaultBackground
        val fg = scheme.defaultForeground
        val dark = ColorUtil.isDark(bg)
        val bar = ColorUtil.mix(bg, fg, if (dark) 0.09 else 0.07)
        val panel = ColorUtil.mix(bg, fg, if (dark) 0.045 else 0.03)
        val shine = ColorUtil.mix(bg, fg, if (dark) 0.16 else 0.02)
        g.color = bg
        g.fillRect(0, 0, width, height)

        val z = l.zoom
        val shapes = shapesFor(l, width / z, height / z)
        g.scale(z.toDouble(), z.toDouble())
        g.color = panel
        shapes.panels.forEach(g::fill)
        g.color = bar
        shapes.bars.forEach(g::fill)
        // The light: a soft band crossing the page, then a pause.
        val cycle = ((System.currentTimeMillis() - started) % SWEEP_MS) / SWEEP_MS.toFloat()
        val t = min(cycle / SWEEP_SHARE, 1f)
        if (t < 1f) {
            val pageWidth = width / z
            val band = max(pageWidth * 0.32f, 260f)
            val x = -band + easeInOut(t) * (pageWidth + 2 * band)
            g.paint = LinearGradientPaint(x - band / 2, 0f, x + band / 2, 0f, floatArrayOf(0f, 0.5f, 1f), arrayOf(ColorUtil.withAlpha(shine, 0.0), shine, ColorUtil.withAlpha(shine, 0.0)))
            shapes.bars.forEach(g::fill)
        }
        shapes.divider?.let {
            g.color = ColorUtil.mix(bg, JBColor.border(), 0.6)
            g.draw(it)
        }
    }

    /** The bars, the quieter panels under some (code blocks), for this size and layout (rebuilt when either changes). */
    private class Shapes(val key: Any, val bars: List<Shape>, val panels: List<Shape>, val divider: Line2D?)

    private var cached: Shapes? = null

    private fun shapesFor(l: Layout, pageWidth: Float, pageHeight: Float): Shapes {
        val key = Triple(l, pageWidth, pageHeight)
        cached?.takeIf { it.key == key }?.let { return it }
        val outline = l.outline && pageWidth >= OUTLINE_MIN_PAGE
        val mainWidth = if (outline) pageWidth - OUTLINE_WIDTH else pageWidth
        val column = min(mainWidth, (l.columnWidth ?: Int.MAX_VALUE).toFloat())
        val pad = if (l.live) LIVE_PAD else PREVIEW_PAD
        val left = (mainWidth - column) / 2 + pad
        val bars = ArrayList<Shape>()
        val panels = ArrayList<Shape>()
        document(bars, panels, l.textSize, left, if (l.live) LIVE_TOP else PREVIEW_TOP, max(column - 2 * pad, 80f), pageHeight)
        if (outline) outline(bars, mainWidth, pageHeight)
        return Shapes(key, bars, panels, if (outline) Line2D.Float(mainWidth, 0f, mainWidth, pageHeight) else null).also { cached = it }
    }

    /** A title, then sections (heading, paragraphs, a code block or a list) down to the bottom. */
    private fun document(shapes: MutableList<Shape>, panels: MutableList<Shape>, size: Float, left: Float, top: Float, width: Float, bottom: Float) {
        val line = (size * LINE).roundToInt().toFloat()
        val text = (size * 0.62f).roundToInt().toFloat()
        var y = top
        fun bars(vararg widths: Float) {
            for (w in widths) {
                shapes.add(pill(left, y + (line - text) / 2, width * w, text))
                y += line
            }
            y += size
        }
        fun heading(level: Int, w: Float) {
            val em = if (level == 1) 1.85f else 1.5f
            val h = (size * em * 0.6f).roundToInt().toFloat()
            val box = (size * em * 1.4f).roundToInt().toFloat()
            shapes.add(pill(left, y + (box - h) / 2, width * w, h))
            y += box + size
        }
        heading(1, 0.34f)
        bars(1f, 0.93f, 0.58f)
        var section = 0
        while (y < bottom) {
            heading(2, SECTION_TITLES[section % SECTION_TITLES.size])
            bars(1f, 0.96f, 0.88f, 0.44f)
            if (section % 2 == 0) {
                // A code block: its quiet panel with a few indented lines of code.
                val h = line * CODE.size + 24f
                panels.add(RoundRectangle2D.Float(left, y, width, h, 12f, 12f))
                for ((i, w) in CODE.withIndex()) {
                    val indent = if (i == 0 || i == CODE.lastIndex) 0f else size * 2
                    shapes.add(pill(left + 16f + indent, y + 12f + i * line + (line - text) / 2, (width - 32f) * w, text))
                }
                y += h + size
            } else {
                for (w in LIST) {
                    val dot = size * 0.34f
                    shapes.add(Ellipse2D.Float(left + size * 0.4f, y + (line - dot) / 2, dot, dot))
                    shapes.add(pill(left + size * 1.6f, y + (line - text) / 2, (width - size * 1.6f) * w, text))
                    y += line
                }
                y += size
            }
            bars(0.9f, 0.36f)
            section++
        }
    }

    /** The outline: its title and the headings, nested a step under the first. */
    private fun outline(shapes: MutableList<Shape>, x0: Float, bottom: Float) {
        val left = x0 + OUTLINE_PAD
        val width = OUTLINE_WIDTH - OUTLINE_PAD * 2
        shapes.add(pill(x0 + 16f, 13f, 54f, 10f))
        var y = 38f
        for ((i, w) in OUTLINE_ITEMS.withIndex()) {
            if (y > bottom) break
            val indent = if (i == 0) 0f else 14f
            shapes.add(pill(left + 12f + indent, y + 9f, (width - 24f - indent) * w, 9f))
            y += 27f
        }
    }

    private fun pill(x: Float, y: Float, w: Float, h: Float) = RoundRectangle2D.Float(x, y, w, h, h, h)

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    companion object {
        private const val FRAME_MS = 16
        private const val FADE_MS = 220
        /** A page that never reports (it failed to load): stop covering whatever it shows. */
        private const val GIVE_UP_MS = 10_000L
        private const val SWEEP_MS = 1_600L
        /** The part of a sweep cycle the light moves; the rest is a pause. */
        private const val SWEEP_SHARE = 0.72f

        // The page's measures (CSS pixels): Live Preview's line padding and first line, the
        // preview's padding, the outline's width and the page width it needs.
        private const val LIVE_PAD = 28f
        private const val LIVE_TOP = 4f
        private const val PREVIEW_PAD = 40f
        private const val PREVIEW_TOP = 28f
        private const val LINE = 1.75f
        private const val OUTLINE_WIDTH = 248f
        private const val OUTLINE_MIN_PAGE = 900f
        private const val OUTLINE_PAD = 14f

        private val SECTION_TITLES = floatArrayOf(0.22f, 0.3f, 0.18f, 0.26f)
        private val LIST = floatArrayOf(0.62f, 0.5f, 0.7f)
        private val CODE = floatArrayOf(0.36f, 0.52f, 0.3f, 0.12f)
        private val OUTLINE_ITEMS = floatArrayOf(0.5f, 0.62f, 0.44f, 0.7f, 0.56f, 0.38f, 0.6f, 0.48f)
    }
}

/** The browser with the skeleton over it, both filling the page area. */
internal class PageStack(private val content: JComponent, overlay: JComponent) : JBLayeredPane() {
    init {
        // `setLayer` first: `add(c, DEFAULT_LAYER)` from Kotlin picks the `add(Component, int index)` overload.
        setLayer(content, DEFAULT_LAYER)
        setLayer(overlay, PALETTE_LAYER)
        add(content)
        add(overlay)
    }

    override fun doLayout() {
        for (c in components) c.setBounds(0, 0, width, height)
    }

    override fun getPreferredSize(): Dimension = content.preferredSize
}
