package dev.nooir.desktop.ui

import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/** Uses Android's exact conversion settings model and token format. */
internal object DesktopConversionDialog {
    fun show(parent: Component, original: MangaConversionOptions): MangaConversionOptions? {
        val preset = JComboBox(arrayOf("Manga", "Manhwa", "Manhua", "Custom"))
        val layout = JComboBox(arrayOf("AUTO", "PAGED", "WEBTOON"))
        val orientation = JComboBox(arrayOf("PORTRAIT", "LANDSCAPE"))
        val crop = JCheckBox("Crop white margins")
        val split = JCheckBox("Split double-page spreads")
        val rtl = JCheckBox("Right-to-left reading")
        val dither = JCheckBox("Dither grayscale")
        val clockwise = JCheckBox("Rotate landscape clockwise")
        val cover = JCheckBox("Include manga cover")
        val missingCover = JCheckBox("Allow export without available cover")
        val color = JCheckBox("Keep color in EPUB/CBZ")
        val extended = JCheckBox("Extended XTC/XTCH metadata (experimental)")
        val contrast = JSpinner(SpinnerNumberModel(50, 0, 100, 1))
        val overlap = JSpinner(SpinnerNumberModel(8, 0, 50, 1))
        val pagesPerXhtml = JSpinner(SpinnerNumberModel(1, 0, 999, 1))

        var updating = false
        fun populate(o: MangaConversionOptions) {
            if (updating) return
            updating = true
            preset.selectedItem = o.preset.takeIf { it in listOf("Manga", "Manhwa", "Manhua") } ?: "Custom"
            layout.selectedItem = o.layout
            orientation.selectedItem = o.orientation
            crop.isSelected = o.cropWhiteMargins
            split.isSelected = o.splitSpreads
            rtl.isSelected = o.rightToLeft
            dither.isSelected = o.dither
            clockwise.isSelected = o.landscapeClockwise
            cover.isSelected = o.includeCover
            missingCover.isSelected = o.allowMissingCover
            color.isSelected = o.epubColor
            extended.isSelected = o.xtcExtendedMetadata
            contrast.value = o.contrast
            overlap.value = o.overlapPercent
            pagesPerXhtml.value = o.epubPagesPerXhtml
            updating = false
        }
        populate(original)
        preset.addActionListener {
            if (updating) return@addActionListener
            val name = preset.selectedItem as String
            if (name != "Custom") populate(MangaConversionOptions.preset(name))
        }

        val panel = JPanel(GridLayout(0, 2, 10, 5)).apply {
            add(JLabel("Preset")); add(preset)
            add(JLabel("Page layout")); add(layout)
            add(JLabel("Orientation")); add(orientation)
            add(JLabel("Contrast (50 = neutral)")); add(contrast)
            add(JLabel("Long-strip overlap %")); add(overlap)
            add(JLabel("EPUB pages per XHTML (0 = chapter)")); add(pagesPerXhtml)
            add(crop); add(split)
            add(rtl); add(dither)
            add(clockwise); add(cover)
            add(missingCover); add(color)
            add(extended); add(JLabel("XTC/XTCH always grayscale"))
        }
        if (JOptionPane.showConfirmDialog(parent, panel, "MangaNooir conversion settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null
        }
        return MangaConversionOptions(
            preset = preset.selectedItem as String,
            layout = layout.selectedItem as String,
            cropWhiteMargins = crop.isSelected,
            splitSpreads = split.isSelected,
            rightToLeft = rtl.isSelected,
            contrast = contrast.value as Int,
            dither = dither.isSelected,
            orientation = orientation.selectedItem as String,
            overlapPercent = overlap.value as Int,
            landscapeClockwise = clockwise.isSelected,
            includeCover = cover.isSelected,
            epubColor = color.isSelected,
            xtcExtendedMetadata = extended.isSelected,
            allowMissingCover = missingCover.isSelected,
            skipUnavailableChapters = original.skipUnavailableChapters,
            epubPagesPerXhtml = pagesPerXhtml.value as Int,
        )
    }
}
