package polina4096.voices

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.rd.fill2DRoundRect
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.ZoneId
import java.util.Timer
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.swing.Icon
import kotlin.concurrent.schedule
import kotlin.concurrent.timer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class VoiceFoldRegionRenderer (
    private val editor: Editor,
    public  var offset: Int,
    private val values: List<Double>,
    private val duration: Float,
    private val creation: String,
    private val path: Path,
)
    : CustomFoldRegionRenderer
{
    private var width = 200
    private var height = 64

    private val clip: Clip = AudioSystem.getClip()
//        .apply { addLineListener(object : LineListener {
//            override fun update(event: LineEvent?) {
//            }
//        }) }

    private var time = 0.0F

    companion object {
        operator fun invoke(editor: Editor, file: File, startOffset: Int): VoiceFoldRegionRenderer {
            // load audio
            val stream = AudioSystem.getAudioInputStream(file)

            // extract frequencies
            val frameCount = stream.frameLength.toInt()
            val dataLength = frameCount * stream.format.sampleSizeInBits * stream.format.channels / 8
            val data = ByteArray(dataLength)
            stream.read(data)

            val sampleSize = stream.format.sampleSizeInBits / 8
            val channelsNum = stream.format.channels
            val samples = mutableListOf<Double>()
            for (idx in 0 until frameCount) {
                val sampleBytes = ByteArray(4) //4byte = int
                for (i in 0 until sampleSize) {
                    sampleBytes[i] = data[idx * sampleSize * channelsNum + i]
                }

                samples.add(
                    ByteBuffer
                        .wrap(sampleBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                        .toDouble()
                )
            }

            // downsample
            val resolution = 40
            val sampleChunkLength = samples.size / resolution

            // find absolute average of each chunk
            val chunks = samples
                .chunked(sampleChunkLength)
                .map { it.fold(0.0) { acc, e -> acc + abs(e) } }
                .map { it / sampleChunkLength }

            // normalize and scale
            val normal = chunks.maxOf { it }
            val min = chunks.average() / 1.25

            val waveform = chunks.map { max((it - min) / (normal - min), 0.0) }

            val indentation = editor.offsetToVisualPosition(startOffset).column
            // indents.getDescriptor(start, start)?.indentLevel ?: 0

            val absolutePath = file.toPath()
            val duration = frameCount / stream.format.frameRate
            val creation = with(
                (Files.getAttribute(absolutePath, "creationTime") as FileTime)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
            ) { "%02d:%02d".format(hour, minute) }

            return VoiceFoldRegionRenderer(
                editor,
                indentation,
                waveform,
                duration,
                creation,
                absolutePath,
            )
        }
    }

    override fun calcWidthInPixels(region: CustomFoldRegion): Int = width
    override fun calcHeightInPixels(region: CustomFoldRegion): Int = height

    override fun calcGutterIconRenderer(region: CustomFoldRegion): GutterIconRenderer {
        class VoiceGutterIconRenderer : GutterIconRenderer() {
            override fun getIcon(): Icon = AllIcons.Gutter.JavadocEdit
            override fun hashCode(): Int = clickAction.hashCode()
            override fun equals(other: Any?): Boolean = false

            override fun getClickAction(): AnAction {
                return object : AnAction() {
                    override fun actionPerformed(e: AnActionEvent) {
                        editor.foldingModel.runBatchFoldingOperation { region.dispose() }
                    }
                }
            }
        }

        return VoiceGutterIconRenderer()
    }

    override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        val r = targetRegion.getBounds()
        val g = g.create() as Graphics2D
        g.translate((UISettings.defFontSize * offset / 2.0).roundToInt(), 0)

        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        val rounding = 36.0
        g.fill2DRoundRect(r, rounding, Color(80, 80, 80, 60))

        val barCount = values.size
        val barSpacing = 1.0
        val scale = 4.0

        val maxBarHeight = 28.0
        val padding = 10.0

        val pos = padding.roundToInt()
        val outerPlayCircleSize = height
        val innerPlayCircleSize = outerPlayCircleSize - padding * 2.0
        val size = innerPlayCircleSize.roundToInt()

        g.color = Color(72, 141, 226, 255)
        g.stroke = BasicStroke((2.0 * scale).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER)

        g.scale(1.0 / scale, 1.0 / scale)

        g.drawOval(
            (r.x + pos * scale).roundToInt(),
            ((r.y + pos) * scale).roundToInt(),
            (size * scale).roundToInt(),
            (size * scale).roundToInt()
        )

        if (clip.isRunning) {
            val pauseSize = size * 0.4
            g.fillRoundRect(
                (((r.x + outerPlayCircleSize / 2 - pauseSize / 6.0 - pauseSize / 3.0) * scale).roundToInt()),
                (((r.y + outerPlayCircleSize / 2 - pauseSize / 2.0) * scale).roundToInt()),
                ((pauseSize / 3.0 * scale).roundToInt()),
                ((pauseSize * scale).roundToInt()),
                16, 16
            )
            g.fillRoundRect(
                (((r.x + outerPlayCircleSize / 2 + pauseSize / 6.0) * scale).roundToInt()),
                (((r.y + outerPlayCircleSize / 2 - pauseSize / 2.0) * scale).roundToInt()),
                ((pauseSize / 3.0 * scale).roundToInt()),
                ((pauseSize * scale).roundToInt()),
                16, 16
            )
        } else {
            g.fillPolygon(
                arrayOf(
                    ((r.x + pos + size * 0.3 + size * 0.05) * scale).roundToInt(),
                    ((r.x + pos + size * 0.7 + size * 0.05) * scale).roundToInt(),
                    ((r.x + pos + size * 0.3 + size * 0.05) * scale).roundToInt()
                ).toIntArray(),
                arrayOf(
                    ((r.y + pos + size * 0.7) * scale).roundToInt(),
                    ((r.y + pos + size / 2  ) * scale).roundToInt(),
                    ((r.y + pos + size * 0.3) * scale).roundToInt()
                ).toIntArray(),
                3
            )
        }

        g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            .deriveFont((11.0f * scale).toFloat())

        val durationString = "%02d:%02d".format((time / 60).roundToInt(), (time % 60).roundToInt())

        g.drawString(
            durationString,
            ((r.x + outerPlayCircleSize) * scale).roundToInt(),
            ((r.y + (height - padding)) * scale).roundToInt()
        )

        for (i in 0 until barCount) {
            val barWidth = (200.0 - outerPlayCircleSize - padding * 1.5) / barCount.toDouble()
            val barHeight = max(values[i] * maxBarHeight, barWidth)

            val progress = time / duration
            if (progress <= i.toDouble() / barCount.toDouble()) {
                g.color = Color(120, 120, 120, 255)
            }

            g.fillRoundRect(
                ((r.x + outerPlayCircleSize + i * barWidth) * scale).roundToInt(),
                ((r.y + (maxBarHeight - barHeight / 2.0) - padding * 0.3) * scale).roundToInt(),
                ((barWidth - barSpacing) * scale).roundToInt(),
                (barHeight * scale).roundToInt(),
                20,
                10
            )
        }

        g.drawString(creation,
            ((r.x + width - 32 - padding) * scale).roundToInt(),
            (((r.y + (height - padding))) * scale).roundToInt()
        )
    }

    fun toggle() {
        if (clip.isRunning) {
            clip.stop()
        } else {
            val time = this@VoiceFoldRegionRenderer.time
            if (duration - time < 0.01F) {
                clip.framePosition = 0
                clip.close()
            }

            if (!clip.isOpen) {
                val file = File(path.toUri())
                if (file.exists()) {
                    clip.open(AudioSystem.getAudioInputStream(file))
                }
            }

            clip.start()
        }

        if (clip.framePosition == 0)
            timer(period = 100L) {
                time = clip.framePosition / clip.format.frameRate
                if (duration - time < 0.01F) {
                    val timer = this
                    Timer().schedule(200L) {
                        timer.cancel()
                        clip.close()
                    }
                }

                editor.contentComponent.repaint()
//                region.repaint()
            }
    }
}