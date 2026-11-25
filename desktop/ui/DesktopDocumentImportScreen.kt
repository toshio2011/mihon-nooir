package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Component
import java.awt.GridLayout
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

internal object DesktopDocumentImportScreen {
    private val active = AtomicBoolean(false)
    fun open(parent: Component) {
        if (active.get()) return
        val chooser = JFileChooser().apply {
            dialogTitle = "Import an unprotected PDF or EPUB"
            fileFilter = FileNameExtensionFilter("PDF or EPUB","pdf","epub")
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val source = chooser.selectedFile
        val pdf = source.extension.equals("pdf",true)
        if (!pdf && !source.extension.equals("epub",true)) return
        val format = JComboBox(arrayOf("EPUB2","EPUB","XTC","XTCH","CBZ"))
        val profile = JComboBox(NooirDeviceProfiles.builtins.map { it.id }.toTypedArray())
        val text = JCheckBox("PDF: include extracted text after each original page")
        text.isEnabled = pdf
        val epubMode = JComboBox(arrayOf("Text + illustrations (XTC/XTCH)", "Image comic (existing converter)"))
        val font = JComboBox(arrayOf("Serif", "SansSerif", "Monospaced"))
        val fontPx = JSpinner(SpinnerNumberModel(24,14,46,1))
        val margin = JSpinner(SpinnerNumberModel(26,8,80,2))
        val lineHeight = JSpinner(SpinnerNumberModel(118,100,200,5))
        val direction = JComboBox(arrayOf("Auto","Left to right","Right to left"))
        if (!pdf) format.selectedItem = "XTC"
        val rendering = JComboBox(arrayOf("FLOYD_STEINBERG","ATKINSON","BAYER_8X8","THRESHOLD"))
        val color = JCheckBox("Preserve color in image archives",false)
        val fields = JPanel(GridLayout(0,1,6,6)).apply {
            add(JLabel(if (pdf) "Original-page PDF; no OCR or arbitrary source-page cap"
                else "EPUB: choose text pagination or existing image comic mode; no DRM bypass"))
            add(JLabel("Export format")); add(format)
            add(JLabel("Reader profile")); add(profile)
            if (pdf) { add(JLabel("Rendering")); add(rendering); add(color); add(text) }
            else {
                add(JLabel("EPUB mode")); add(epubMode)
                add(JLabel("Text font / size (pixels)")); add(font); add(fontPx)
                add(JLabel("Text margin (pixels) / line height (%)")); add(margin); add(lineHeight)
                add(JLabel("Reading direction")); add(direction)
            }
        }
        if (JOptionPane.showConfirmDialog(parent, fields,"Document conversion",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
        val outputFormat = ExportFormat.valueOf(format.selectedItem as String)
        val device = profile.selectedItem as String
        if (!NooirDeviceProfiles.forId(device).accepts(outputFormat) ||
            (text.isSelected && outputFormat !in setOf(ExportFormat.EPUB,ExportFormat.EPUB2))) {
            JOptionPane.showMessageDialog(parent,"Choose a compatible device/format; selectable text companion needs EPUB.")
            return
        }
        val prose = !pdf && epubMode.selectedIndex == 0
        if (prose && outputFormat !in setOf(ExportFormat.XTC, ExportFormat.XTCH)) {
            JOptionPane.showMessageDialog(parent,"Text EPUB pagination requires XTC or XTCH. Choose image comic mode only for image books.")
            return
        }
        val typography = DesktopTextEpubImport.Settings(fontPx.value as Int, margin.value as Int,
            lineHeight.value as Int, font.selectedItem as String, direction.selectedItem as String)
        val options = if (prose) MangaConversionOptions().copy(renderAlgorithm="THRESHOLD")
        else if (pdf) MangaConversionOptions().copy(
            rightToLeft=false,renderAlgorithm=rendering.selectedItem as String,epubColor=color.isSelected)
        else DesktopConversionDialog.show(parent,MangaConversionOptions.preset("Manga")) ?: return
        val save = JFileChooser().apply {
            dialogTitle = "Save converted document (new filename)"
            selectedFile = File(DesktopUiModel.suggestedFile(source.nameWithoutExtension,outputFormat))
        }
        if (save.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val target = save.selectedFile.absoluteFile
        if (target.exists() || !target.extension.equals(outputFormat.extension,true)) {
            JOptionPane.showMessageDialog(parent,"Choose a new filename ending in ." + outputFormat.extension)
            return
        }
        if (!active.compareAndSet(false,true)) return
        val canceled = AtomicBoolean(false)
        val status = JProgressBar().apply { isStringPainted = true; isIndeterminate = true; string = "Preparing document" }
        val stop = JButton(if (pdf || prose) "Cancel conversion" else "Cancel import before conversion")
        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent),"Document conversion",
            java.awt.Dialog.ModalityType.MODELESS).apply {
            defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
            add(status,java.awt.BorderLayout.CENTER); add(stop,java.awt.BorderLayout.SOUTH)
            setSize(420,120); setLocationRelativeTo(parent)
        }
        stop.addActionListener { canceled.set(true); stop.isEnabled=false; status.string = "Cancellation requested" }
        object : SwingWorker<Int,Pair<Int,Int>>() {
            override fun doInBackground(): Int = if (pdf) DesktopPdfImport.export(source,target,outputFormat,device,
                options,text.isSelected,{ canceled.get() }) { n,total -> publish(n to total) }
                else if (prose) DesktopTextEpubImport.export(source,target,outputFormat,device,typography,options,{ canceled.get() })
                else DesktopImageEpubImport.export(source,target,outputFormat,device,options,{ canceled.get() })
            override fun process(chunks: MutableList<Pair<Int,Int>>) {
                val (n,total) = chunks.last()
                status.isIndeterminate = false; status.maximum = total; status.value=n
                status.string = if (canceled.get()) "Canceling after page $n" else "PDF page $n / $total"
            }
            override fun done() {
                active.set(false); dialog.dispose()
                try {
                    val count=get()
                    JOptionPane.showMessageDialog(parent,"Converted $count source page(s). Output: " + target.name)
                } catch(e: Exception) {
                    val cause=(e as? ExecutionException)?.cause ?: e
                    JOptionPane.showMessageDialog(parent,cause.message ?: "Document conversion failed",
                        "Document conversion",JOptionPane.WARNING_MESSAGE)
                }
            }
        }.execute()
        dialog.isVisible=true
    }
}
