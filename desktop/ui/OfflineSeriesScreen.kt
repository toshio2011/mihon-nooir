package dev.nooir.desktop.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel

/**
 * Group downloaded CBZ chapters by their actual Suwayomi manga ID and title.
 * Works entirely offline; missing chapters remain visible for manual repair.
 */
internal object OfflineSeriesScreen {
    fun open() {
        OfflineSeriesWindow().isVisible = true
    }
}

private class OfflineSeriesWindow : JFrame("MihonNooir — Downloaded manga") {
    private val catalog = OfflineSeriesCatalog()
    private val library = LocalLibraryStore()
    private val seriesModel = DefaultListModel<OfflineSeries>()
    private val chapterModel = DefaultListModel<OfflineSeriesChapter>()
    private val seriesList = JList(seriesModel)
    private val chapterList = JList(chapterModel)
    private val read = JButton("Read chapter")
    private val forget = JButton("Remove from series")
    private val refresh = JButton("Refresh")
    private val status = JLabel("Saved chapters appear here after you import them from Suwayomi")

    init {
        minimumSize = Dimension(700, 430)
        setSize(1040, 670)
        setLocationRelativeTo(null)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        seriesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        chapterList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        seriesList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean,
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(
                    list, value, index, selected, focus) as JLabel
                if (value is OfflineSeries) {
                    val available = value.chapters.count { it.available }
                    label.text = "${value.title}  ($available / ${value.chapters.size} chapters)"
                    label.toolTipText = "Series ID: ${value.mangaId}"
                    label.border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
                }
                return label
            }
        }
        chapterList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean,
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(
                    list, value, index, selected, focus) as JLabel
                if (value is OfflineSeriesChapter) {
                    label.text = "${if (value.available) "●" else "○"} ${value.chapterTitle}"
                    label.toolTipText = if (value.available) value.file.path
                        else "Comic file missing: ${value.file.path}"
                    label.border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
                }
                return label
            }
        }
        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, JScrollPane(seriesList), JScrollPane(chapterList)
        ).apply {
            resizeWeight = 0.43
            dividerLocation = 380
        }
        add(split, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(this@OfflineSeriesWindow.read)
            add(this@OfflineSeriesWindow.forget)
            add(this@OfflineSeriesWindow.refresh)
        }, BorderLayout.NORTH)
        add(status.apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        }, BorderLayout.SOUTH)

        seriesList.addListSelectionListener {
            if (!it.valueIsAdjusting) loadChapters()
        }
        chapterList.addListSelectionListener {
            if (!it.valueIsAdjusting) setActions()
        }
        chapterList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && chapterList.selectedValue?.available == true) readSelected()
            }
        })
        read.addActionListener { readSelected() }
        forget.addActionListener { forgetSelected() }
        refresh.addActionListener { reload() }
        reload()
    }

    private fun setActions() {
        read.isEnabled = chapterList.selectedValue?.available == true
        forget.isEnabled = chapterList.selectedValue != null
    }

    private fun loadChapters(previousChapterId: Int? = null) {
        val selected = seriesList.selectedValue
        chapterModel.clear()
        selected?.chapters?.forEach(chapterModel::addElement)
        val index = (0 until chapterModel.size()).firstOrNull {
            chapterModel[it].chapterId == previousChapterId
        }
        if (index != null) chapterList.selectedIndex = index
        status.text = if (selected == null) "Choose a manga series" else
            "${selected.title} — ${selected.chapters.size} saved chapter(s), sorted in reading order"
        setActions()
    }

    private fun reload() {
        val selectedId = seriesList.selectedValue?.mangaId
        val selectedChapterId = chapterList.selectedValue?.chapterId
        try {
            val series = catalog.series()
            seriesModel.clear()
            series.forEach(seriesModel::addElement)
            val index = (0 until seriesModel.size()).firstOrNull {
                seriesModel[it].mangaId == selectedId
            } ?: if (seriesModel.isEmpty) null else 0
            if (index != null) {
                seriesList.selectedIndex = index
                loadChapters(selectedChapterId)
            } else {
                chapterModel.clear()
                status.text = "No downloaded manga yet. Save chapters from Suwayomi to get started."
            }
        } catch (e: Exception) {
            error(e.message ?: "Cannot read the offline manga index")
        }
        setActions()
    }

    private fun readSelected() {
        val chapter = chapterList.selectedValue ?: return
        if (!chapter.available) return
        if (LocalComicReader.open(chapter.file, this)) {
            try {
                library.markOpened(chapter.file)
            } catch (e: Exception) {
                error("Reader opened but could not update recent history: ${e.message}")
            }
        }
    }

    private fun forgetSelected() {
        val chapter = chapterList.selectedValue ?: return
        val answer = JOptionPane.showConfirmDialog(this,
            "Remove ${chapter.chapterTitle} from the grouped offline series view?\n" +
                "This does not delete the CBZ or remove it from your local library.",
            "Remove series entry", JOptionPane.YES_NO_OPTION)
        if (answer != JOptionPane.YES_OPTION) return
        try {
            catalog.forget(chapter.mangaId, chapter.chapterId)
            reload()
        } catch (e: Exception) {
            error(e.message ?: "Cannot remove series metadata")
        }
    }

    private fun error(message: String) {
        JOptionPane.showMessageDialog(this, message,
            "MihonNooir downloaded manga", JOptionPane.WARNING_MESSAGE)
    }
}
