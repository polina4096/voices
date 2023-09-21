package polina4096.intelligentvoices

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.project.stateStore
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.io.File
import javax.sound.sampled.*
import javax.swing.*
import kotlin.concurrent.thread

class RecordVoiceMessageAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = object : DialogWrapper(e.project!!) {
        val basePath = e.project!!.stateStore.projectBasePath
        val tempPath = basePath.resolve(".idea/recording.wav")
        var isRecording = false

        val format = AudioFormat(48000.0f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val microphone = (AudioSystem.getLine(info) as TargetDataLine)
            .apply { open(format, this.bufferSize) }

        val out = AudioInputStream(microphone)

        init {
            title = "Record a Voice Message"
            init()
        }

        override fun isOKActionEnabled(): Boolean = !isRecording

        override fun doOKAction() {
            val name = java.time.Instant.now().toEpochMilli()
            val path = ".idea/${name}.wav"
            val dest = File(basePath.resolve(path).toUri())
            if (!File(tempPath.toUri()).renameTo(dest)) {
                e.project!!.error("Failed to record voice message!")
                return
            }

            val editor = FileEditorManager.getInstance(e.project!!).selectedTextEditor ?: run { close(OK_EXIT_CODE); return }

            val startOffset = editor.caretModel.offset
            val line = StringUtil.offsetToLineNumber(editor.document.text, startOffset)

            WriteCommandAction.runWriteCommandAction(e.project!!) {
                editor.document.insertString(startOffset, "// voice:${path}")
                val key = TextAttributesKey.createTextAttributesKey("voice_message")
                val highlighter = editor.markupModel.addLineHighlighter(key, line, HighlighterLayer.FIRST)

                makeVoiceFold(editor, highlighter, VoiceFoldRegionRenderer(editor, dest, startOffset))
            }

            close(OK_EXIT_CODE)
        }

        override fun doCancelAction() {
            if (microphone.isActive)
                microphone.stop()

            if (microphone.isOpen)
                microphone.close()

            close(CANCEL_EXIT_CODE)
        }

        override fun createLeftSideActions(): Array<Action> {
            return arrayOf(
                object : AbstractAction("Start recording") {
                    override fun isEnabled(): Boolean
                        = AudioSystem.isLineSupported(microphone.lineInfo)

                    override fun actionPerformed(e: ActionEvent?) {
                        if (!isRecording) {
                            isRecording = true
                            putValue(Action.NAME, "Stop recording")

                            microphone.open()
                            microphone.start()
                            thread { AudioSystem.write(out, AudioFileFormat.Type.WAVE, File(tempPath.toUri())) }
                        } else {
                            isRecording = false
                            putValue(Action.NAME, "Start recording")

                            microphone.stop()
                            microphone.close()
                        }
                    }
                }
            )
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout()).apply { preferredSize = Dimension(320, 0) }
        }
    }.show()
}