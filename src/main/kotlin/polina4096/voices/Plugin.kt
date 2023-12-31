package polina4096.voices

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.refactoring.suggested.range
import com.intellij.refactoring.suggested.startOffset
import javax.swing.Icon
import kotlin.math.roundToInt

fun Project.error(string: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Voices")
        .createNotification(string, NotificationType.ERROR)
        .notify(this)
}

fun Editor.makeVoiceFold(highlighter: RangeHighlighter, makeVoiceFoldRegion: VoiceFoldRegionRenderer) {
    this.foldingModel.runBatchFoldingOperation {
        val position = StringUtil.offsetToLineNumber(highlighter.document.text, highlighter.startOffset)
        this.foldingModel.addCustomLinesFolding(position, position, makeVoiceFoldRegion)
    }
}

fun Editor.makeVoiceComment(line: Int, voiceFoldRegionRenderer: () -> VoiceFoldRegionRenderer?) {
    class VoiceGutterIconRenderer(val highlighter: RangeHighlighter) : GutterIconRenderer() {
        override fun getIcon(): Icon = AllIcons.Gutter.JavadocRead
        override fun hashCode(): Int = this.clickAction.hashCode()
        override fun equals(other: Any?): Boolean = other is VoiceGutterIconRenderer

        override fun getClickAction(): AnAction {
            return object : AnAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    val startOffset = highlighter.range?.startOffset ?: return
                    val indentation = this@makeVoiceComment.offsetToVisualPosition(startOffset).column

                    val render = voiceFoldRegionRenderer()
                    if (render != null) {
                        render.offset = indentation
                        this@makeVoiceComment.makeVoiceFold(highlighter, render)
                    }
                }
            }
        }
    }

    val markupModel = this.markupModel
    val highlighter =
        markupModel.allHighlighters.firstOrNull { StringUtil.offsetToLineNumber(this.document.text, it.startOffset) == line } ?:
        markupModel.addLineHighlighter(TextAttributesKey.createTextAttributesKey("voice_message"), line, HighlighterLayer.FIRST)

    highlighter.gutterIconRenderer = VoiceGutterIconRenderer(highlighter)
}

class MyProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                editor.addEditorMouseListener(object : EditorMouseListener {
                    override fun mouseClicked(event: EditorMouseEvent) {
                        if (event.area != EditorMouseEventArea.EDITING_AREA) return
                        val region = editor.foldingModel.allFoldRegions
                            .firstOrNull { StringUtil.offsetToLineNumber(editor.document.text, it.startOffset) == event.logicalPosition.line } ?: return

                        val mouseEvent = event.mouseEvent
                        val regionPos = editor.offsetToXY(region.startOffset)

                        val foldHeight = 64
                        val padding = 10
                        val size = foldHeight - padding * 2

                        if (region is CustomFoldRegion && region.renderer is VoiceFoldRegionRenderer){
                            val renderer: VoiceFoldRegionRenderer = region.renderer as VoiceFoldRegionRenderer
                            val xOffset = (UISettings.defFontSize * renderer.offset / 2.0).roundToInt()
                            if (mouseEvent.x > regionPos.x + padding + xOffset && mouseEvent.x < regionPos.x + padding + size + xOffset
                                &&  mouseEvent.y > regionPos.y + padding           && mouseEvent.y < regionPos.y + padding + size) {
                                renderer.toggle()
                            }
                        }
                    }
                })
            }
        }, project)

        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeListener {
            override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
                if (event.child is PsiComment) {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

                    val startOffset = event.child.startOffset
                    val line = StringUtil.offsetToLineNumber(event.child.containingFile.text, startOffset)
                    val highlighter = editor.markupModel.allHighlighters
                        .firstOrNull { StringUtil.offsetToLineNumber(editor.document.text, it.startOffset) == line }

                    highlighter?.dispose()
                }
            }

            override fun beforeChildAddition(event: PsiTreeChangeEvent) { }
            override fun beforeChildReplacement(event: PsiTreeChangeEvent) { }
            override fun beforeChildMovement(event: PsiTreeChangeEvent) { }
            override fun beforeChildrenChange(event: PsiTreeChangeEvent) { }
            override fun beforePropertyChange(event: PsiTreeChangeEvent) { }
            override fun childRemoved(event: PsiTreeChangeEvent) { }
            override fun childAdded(event: PsiTreeChangeEvent) { }
            override fun childReplaced(event: PsiTreeChangeEvent) { }
            override fun childrenChanged(event: PsiTreeChangeEvent) { }
            override fun childMoved(event: PsiTreeChangeEvent) { }
            override fun propertyChanged(event: PsiTreeChangeEvent) { }

        }, project)
    }
}