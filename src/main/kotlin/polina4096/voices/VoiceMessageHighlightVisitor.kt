package polina4096.voices

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.project.stateStore
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import java.io.File

class VoiceMessageHighlightVisitor : HighlightVisitor {
    override fun suitableForFile(file: PsiFile): Boolean = true

    override fun visit(element: PsiElement) {
        if (element !is PsiComment) {
            return
        }

        val project = element.project
        val index = element.text.indexOf("voice:")
        if (index == -1) return

        val audioPath = element.text
            .substring(index + "voice:".length)
            .takeWhile { c -> c != '\n' }

        val absolutePath = project.stateStore.projectBasePath.resolve(audioPath)
        val file = File(absolutePath.toUri())
        val startOffset = element.startOffset

        ApplicationManager.getApplication().invokeLater {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@invokeLater
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, startOffset)

            val highlighter = editor.markupModel.allHighlighters
                .firstOrNull { StringUtil.offsetToLineNumber(element.containingFile.text, it.startOffset) == line }

            if (!file.exists()) {
                highlighter?.dispose()
                return@invokeLater
            }

            val renderer = VoiceFoldRegionRenderer(editor, file, startOffset)
            if (renderer != null ) {
                editor.makeVoiceFold(highlighter ?: editor.markupModel.addLineHighlighter(TextAttributesKey.createTextAttributesKey("voice_message"), line, HighlighterLayer.FIRST), renderer)
                editor.makeVoiceComment(line) { renderer }
            }
        }
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        try { action.run() } catch (_: Throwable) { }
        return true
    }

    override fun clone(): HighlightVisitor = VoiceMessageHighlightVisitor()
}