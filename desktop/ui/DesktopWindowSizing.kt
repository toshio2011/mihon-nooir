package dev.nooir.desktop.ui

import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

/** Desktop-only layout helpers; never make an initial frame larger than the usable screen. */
internal object DesktopWindowSizing {
    fun fit(frame: JFrame, preferred: Dimension, minimum: Dimension) {
        val work = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val availableWidth = (work.width - 16).coerceAtLeast(1)
        val availableHeight = (work.height - 16).coerceAtLeast(1)
        val width = minOf(preferred.width, availableWidth)
        val height = minOf(preferred.height, availableHeight)
        frame.minimumSize = Dimension(minOf(minimum.width, width), minOf(minimum.height, height))
        frame.setBounds(work.x + (work.width - width) / 2,
            work.y + (work.height - height) / 2, width, height)
    }

    /** Keep a complete single row accessible even on a narrow logical screen. */
    fun toolbar(row: JPanel): JScrollPane = JScrollPane(
        row, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBar.unitIncrement = 24
        // Reserve scrollbar height even before it appears, so the buttons
        // cannot be vertically clipped when shrinking the frame.
        val height = row.preferredSize.height + horizontalScrollBar.preferredSize.height + 4
        preferredSize = Dimension(1, height)
        minimumSize = Dimension(1, height)
    }
}
