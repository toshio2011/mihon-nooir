package dev.nooir.desktop.ui

import java.awt.BorderLayout
import java.awt.Component
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

    /**
     * One-click offline resume from the local bookshelf. Returns false when
     * there is no history or the selected downloaded CBZ cannot open.
     */
    fun resumeLast(parent: Component?): Boolean {
        try {
            val catalog = OfflineSeriesCatalog()
            val library = LocalLibraryStore()
            val target = OfflineReadingResume.mostRecent(catalog.series(), library.books())
                ?: run {
                    JOptionPane.showMessageDialog(parent,
                        "No previously read downloaded chapter is available. " +
                            "Open Downloaded series to start reading a saved manga.",
                        "MihonNooir Continue reading", JOptionPane.INFORMATION_MESSAGE)
                    return false
                }
            val sequence = ReaderChapterSequence(target.series.title,
                target.series.chapters.map {
                    ReaderChapter(it.chapterId, it.chapterTitle, it.file)
                }, target.series.chapters.indexOfFirst { it.chapterId == target.chapter.chapterId })
            if (!LocalComicReader.open(target.chapter.file, parent, sequence)) return false
            library.markOpened(target.chapter.file)
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent,
                e.message ?: "Cannot resume downloaded manga",
                "MihonNooir Continue reading", JOptionPane.WARNING_MESSAGE)
            return false
        }
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
    private val continueSelected = JButton("Start / Continue selected series")
    private val continueLatest = JButton("Continue last read")
    private var librarySnapshot: List<LocalLibraryBook> = emptyList()
    private var resumeTargets: Map<Int, OfflineResumeTarget> = emptyMap()
    private var recentTarget: OfflineResumeTarget? = null
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
                    val resume = resumeTargets[value.mangaId]
                    val progress = if (resume?.previouslyRead == true)
                        "  · Continue: ${resume.chapter.chapterTitle.take(46)}" else ""
                    label.text = "${value.title}  ($available / ${value.chapters.size} chapters)$progress"
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
            add(this@OfflineSeriesWindow.continueSelected)
            add(this@OfflineSeriesWindow.continueLatest)
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
        continueSelected.addActionListener { continueSelectedSeries() }
        continueLatest.addActionListener { continueRecentSeries() }
        forget.addActionListener { forgetSelected() }
        refresh.addActionListener { reload() }
        reload()
    }

    private fun setActions() {
        read.isEnabled = chapterList.selectedValue?.available == true
        continueSelected.isEnabled = seriesList.selectedValue?.let {
            resumeTargets[it.mangaId] != null
        } == true
        continueLatest.isEnabled = recentTarget != null
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
            librarySnapshot = library.books()
            resumeTargets = series.mapNotNull {
                OfflineReadingResume.forSeries(it, librarySnapshot)?.let { target ->
                    it.mangaId to target
                }
            }.toMap()
            recentTarget = OfflineReadingResume.mostRecent(series, librarySnapshot)
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

    private fun continueSelectedSeries() {
        val manga = seriesList.selectedValue ?: return
        try {
            // Refresh history on click: a separate reader might have advanced
            // to another chapter since this list was last painted.
            val target = OfflineReadingResume.forSeries(manga, library.books())
                ?: return error("No readable downloaded chapters in this series")
            openChapter(target.series, target.chapter)
        } catch (e: Exception) {
            error(e.message ?: "Cannot resume this series")
        }
    }

    private fun continueRecentSeries() {
        if (OfflineSeriesScreen.resumeLast(this)) reload()
    }

    private fun readSelected() {
        val chapter = chapterList.selectedValue ?: return
        val manga = seriesList.selectedValue ?: return
        openChapter(manga, chapter)
    }

    private fun openChapter(manga: OfflineSeries, chapter: OfflineSeriesChapter) {
        if (!chapter.available) return
        val chapters = manga.chapters.map { ReaderChapter(it.chapterId, it.chapterTitle, it.file) }
        val index = chapters.indexOfFirst { it.id == chapter.chapterId }
        if (index < 0) return
        val readerSequence = ReaderChapterSequence(manga.title, chapters, index)
        if (LocalComicReader.open(chapter.file, this, readerSequence)) {
            try {
                library.markOpened(chapter.file)
                reload()
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
