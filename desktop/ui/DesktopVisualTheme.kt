package dev.nooir.desktop.ui

import dev.nooir.desktop.DesktopImageDecoder
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiMangaCard
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

/** Windows-native visual direction inspired by Suwayomi WebUI, not copied UI assets. */
internal object DesktopVisualTheme {
    val background = Color(18, 20, 27)
    val surface = Color(28, 31, 40)
    val elevated = Color(35, 39, 51)
    val selected = Color(45, 54, 75)
    val accent = Color(146, 166, 248)
    val foreground = Color(239, 241, 248)
    val muted = Color(168, 175, 193)
    val divider = Color(56, 62, 79)

    /** Call after Swing sets Nimbus, before creating the primary desktop window. */
    fun install() {
        val values = mapOf(
            "control" to surface, "info" to elevated, "nimbusBase" to Color(39, 45, 63),
            "nimbusBlueGrey" to Color(48, 53, 69), "nimbusLightBackground" to elevated,
            "nimbusSelectionBackground" to selected, "nimbusFocus" to accent,
            "nimbusOrange" to accent, "text" to foreground, "textForeground" to foreground,
            "controlText" to foreground, "textHighlight" to selected,
            "textHighlightText" to foreground, "textInactiveText" to muted,
            "Table.background" to surface, "List.background" to surface,
            "List.foreground" to foreground, "List.selectionBackground" to selected,
            "List.selectionForeground" to foreground, "Panel.background" to background,
            "ScrollPane.background" to background, "Viewport.background" to background,
        )
        for ((key, value) in values) UIManager.put(key, value)
        UIManager.put("defaultFont", Font("SansSerif", Font.PLAIN, 13))
    }

    fun nav(button: JButton, primary: Boolean = false) {
        button.font = Font("SansSerif", if (primary) Font.BOLD else Font.PLAIN, 13)
        button.background = if (primary) selected else surface
        button.foreground = if (primary) foreground else muted
        button.border = EmptyBorder(10, 14, 10, 14)
        button.isFocusPainted = false
        button.isOpaque = true
        button.horizontalAlignment = SwingConstants.LEFT
        button.alignmentX = Component.LEFT_ALIGNMENT
        button.maximumSize = Dimension(Int.MAX_VALUE, 42)
    }

    fun section(title: String): JLabel = JLabel(title).apply {
        font = Font("SansSerif", Font.BOLD, 11)
        foreground = muted
        border = EmptyBorder(18, 13, 9, 0)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    fun pane(title: String): javax.swing.border.Border =
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(divider),
            BorderFactory.createTitledBorder(
                EmptyBorder(9, 11, 7, 11), title,
                javax.swing.border.TitledBorder.LEADING, javax.swing.border.TitledBorder.TOP,
                Font("SansSerif", Font.BOLD, 13), foreground
            )
        )

    fun brand(): JPanel = JPanel(BorderLayout(12, 0)).apply {
        background = background
        border = EmptyBorder(8, 10, 12, 10)
        add(JLabel("MIHONNOOIR").apply {
            foreground = foreground
            font = Font("SansSerif", Font.BOLD, 21)
        }, BorderLayout.WEST)
        add(JLabel("DESKTOP  /  Read manga · Convert for XTEINK").apply {
            foreground = muted
            font = Font("SansSerif", Font.PLAIN, 12)
        }, BorderLayout.CENTER)
    }

    fun styleMain(window: javax.swing.JFrame) {
        window.contentPane.background = background
        // Keep native input painters, disabled colors and keyboard focus behavior.
        // Restyle only low-risk surfaces/buttons, never mutate text or selection.
        fun visit(c: Component) {
            when (c) {
                is JPanel -> if (c.background != selected && c.background != elevated) {
                    c.background = if (c.border is EmptyBorder) background else surface
                }
                is javax.swing.JList<*> -> {
                    c.background = surface
                    c.foreground = foreground
                    c.selectionBackground = selected
                    c.selectionForeground = foreground
                }
                is JButton -> if (c.background != selected) {
                    c.foreground = foreground
                    c.isFocusPainted = false
                    c.margin = Insets(7, 10, 7, 10)
                }
            }
            if (c is java.awt.Container) c.components.forEach(::visit)
        }
        visit(window.contentPane)
    }
}

/** Only fetch covers for user-requested, explicitly browsed Suwayomi manga. */
internal class DesktopMangaCoverCache : AutoCloseable {
    private val worker = Executors.newFixedThreadPool(2) { work ->
        Thread(work, "nooir-desktop-cover").apply { isDaemon = true }
    }
    private val icons = ConcurrentHashMap<String, ImageIcon>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val placeholder = ImageIcon(BufferedImage(96, 132, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().useGraphics { g ->
            g.color = DesktopVisualTheme.elevated
            g.fillRect(0, 0, width, height)
            g.color = DesktopVisualTheme.divider
            g.fillRoundRect(12, 18, 40, 53, 8, 8)
            g.color = DesktopVisualTheme.accent
            g.fillRect(17, 27, 30, 3)
            g.fillRect(17, 39, 22, 3)
        }
    })
    private inline fun Graphics2D.useGraphics(block: (Graphics2D) -> Unit) {
        try { block(this) } finally { dispose() }
    }
    @Volatile private var closed = false

    fun icon(
        item: SuwayomiMangaCard, endpoint: String?, authorization: String?,
        repaint: () -> Unit,
    ): ImageIcon {
        if (item.thumbnailUrl.isNullOrBlank() || endpoint.isNullOrBlank() || closed) return placeholder
        val key = "$endpoint#${item.id}"
        icons[key]?.let { return it }
        if (pending.size >= 24 || key in missing || !pending.add(key)) return placeholder
        worker.execute {
            try {
                val bytes = SuwayomiClient(endpoint, authorization).thumbnail(item.id)
                if (bytes == null) { missing.add(key); return@execute }
                val decoded = DesktopImageDecoder.decode(bytes, 4_000_000)
                val scaled = try {
                    val img = BufferedImage(96, 132, BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics()
                    try {
                        g.color = DesktopVisualTheme.elevated
                        g.fillRect(0, 0, img.width, img.height)
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        val factor = minOf(img.width.toDouble()/decoded.width, img.height.toDouble()/decoded.height)
                        val w = (decoded.width*factor).toInt().coerceAtLeast(1)
                        val h = (decoded.height*factor).toInt().coerceAtLeast(1)
                        g.drawImage(decoded, (96-w)/2, (132-h)/2, w, h, null)
                    } finally { g.dispose() }
                    ImageIcon(img)
                } finally { decoded.flush() }
                if (!closed) {
                    if (icons.size > 96) icons.clear()
                    icons[key] = scaled
                    SwingUtilities.invokeLater { if (!closed) repaint() }
                }
            } catch (_: Exception) {
                missing.add(key)
            } finally { pending.remove(key) }
        }
        return placeholder
    }

    override fun close() { closed = true; worker.shutdownNow(); icons.clear() }
}

/** Cover-led browse rows with titles and author information; same selection model. */
internal class DesktopMangaCardRenderer(
    private val covers: DesktopMangaCoverCache,
    private val endpoint: () -> String?,
    private val repaint: () -> Unit,
) : JPanel(BorderLayout(12, 0)), ListCellRenderer<SuwayomiMangaCard> {
    private val art = JLabel().apply {
        preferredSize = Dimension(96, 132)
        horizontalAlignment = SwingConstants.CENTER
    }
    private val title = JLabel().apply {
        font = Font("SansSerif", Font.BOLD, 14)
    }
    private val byline = JLabel().apply {
        font = Font("SansSerif", Font.PLAIN, 12)
    }
    private val tag = JLabel().apply {
        font = Font("SansSerif", Font.BOLD, 10)
    }
    private var grid = true
    init {
        border = EmptyBorder(8, 12, 8, 12)
        title.putClientProperty("html.disable", true)
        byline.putClientProperty("html.disable", true)
        setGrid(true)
    }

    fun setGrid(enabled: Boolean) {
        grid = enabled
        removeAll()
        val labels = JPanel(java.awt.GridLayout(3, 1, 0, 2)).apply {
            isOpaque = false
            add(title); add(byline); add(tag)
        }
        listOf(title, byline, tag).forEach {
            it.horizontalAlignment = if (enabled) SwingConstants.CENTER else SwingConstants.LEFT
        }
        if (enabled) {
            add(art, BorderLayout.CENTER)
            add(labels, BorderLayout.SOUTH)
        } else {
            add(art, BorderLayout.WEST)
            add(labels, BorderLayout.CENTER)
        }
        revalidate()
    }

    override fun getListCellRendererComponent(
        list: JList<out SuwayomiMangaCard>, value: SuwayomiMangaCard,
        index: Int, isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        background = if (isSelected) DesktopVisualTheme.selected else DesktopVisualTheme.surface
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(3, 3, 3, 3, DesktopVisualTheme.background),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(if (cellHasFocus || isSelected) DesktopVisualTheme.accent else DesktopVisualTheme.divider),
                EmptyBorder(6, 7, 6, 7)))
        title.text = value.title.take(if (grid) 40 else 110)
        getAccessibleContext()?.accessibleName = value.title
        title.foreground = DesktopVisualTheme.foreground
        byline.text = value.author?.take(if (grid) 30 else 90).orEmpty().ifBlank { "Manga" }
        byline.foreground = DesktopVisualTheme.muted
        tag.text = if (value.inLibrary) "IN LIBRARY" else "AVAILABLE TO READ"
        tag.foreground = if (value.inLibrary) DesktopVisualTheme.accent else DesktopVisualTheme.muted
        art.icon = covers.icon(value, endpoint(), System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"), repaint)
        return this
    }
}
