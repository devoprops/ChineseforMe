package com.example.chineseforme.ui.stroke

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.graphics.PathParser
import com.example.chineseforme.data.stroke.CharacterStrokeData
import com.example.chineseforme.data.stroke.mmahToView
import com.example.chineseforme.domain.stroke.StrokePracticeSession
import com.example.chineseforme.ui.theme.InkBrown
import com.example.chineseforme.ui.theme.TileEdge
import com.example.chineseforme.ui.theme.TileFace
import com.example.chineseforme.ui.theme.TileSelected

private const val VIEW = 1024f

@Composable
fun StrokeCanvas(
    data: CharacterStrokeData?,
    completedStrokeCount: Int,
    hintStrokeIndex: Int?,
    guideDimLevel: Int,
    interactive: Boolean,
    onStrokeFinished: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val guidePaths = remember(data) {
        data?.strokePaths?.mapNotNull { parseMmahPath(it) }.orEmpty()
    }
    val guideAlpha = StrokePracticeSession.guideAlphaForDimLevel(guideDimLevel)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(TileFace)
            .then(
                if (interactive && data != null) {
                    Modifier.pointerInput(data.character, completedStrokeCount, interactive) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                val size = this.size
                                val pts = currentStroke.map {
                                    screenToView(it, size.width.toFloat(), size.height.toFloat())
                                }
                                currentStroke = emptyList()
                                if (pts.size >= 2) onStrokeFinished(pts)
                            },
                            onDragCancel = { currentStroke = emptyList() }
                        )
                    }
                } else Modifier
            )
    ) {
        val scale = minOf(size.width, size.height) / VIEW
        val dx = (size.width - VIEW * scale) / 2f
        val dy = (size.height - VIEW * scale) / 2f

        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            guidePaths.forEachIndexed { index, path ->
                val color = when {
                    hintStrokeIndex == index -> TileSelected
                    index < completedStrokeCount -> InkBrown.copy(alpha = 0.85f)
                    else -> TileEdge.copy(alpha = guideAlpha)
                }
                val width = when {
                    hintStrokeIndex == index -> 28f
                    index < completedStrokeCount -> 22f
                    else -> 18f
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (currentStroke.size >= 2) {
            val userPath = Path().apply {
                moveTo(currentStroke.first().x, currentStroke.first().y)
                currentStroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = userPath,
                color = InkBrown,
                style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun screenToView(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
    val scale = minOf(width, height) / VIEW
    val dx = (width - VIEW * scale) / 2f
    val dy = (height - VIEW * scale) / 2f
    val x = (offset.x - dx) / scale
    val y = (offset.y - dy) / scale
    return x to y
}

/**
 * Parse SVG path in MMAH coords and map into viewBox space (y down).
 */
fun parseMmahPath(svgPath: String): Path? {
    return try {
        val androidPath = PathParser.createPathFromPathData(svgPath) ?: return null
        val measure = PathMeasure(androidPath, false)
        val coords = FloatArray(2)
        val out = android.graphics.Path()
        var started = false
        var distance = 0f
        val length = measure.length
        val step = (length / 80f).coerceAtLeast(1f)
        while (distance <= length) {
            measure.getPosTan(distance, coords, null)
            val (vx, vy) = mmahToView(coords[0], coords[1])
            if (!started) {
                out.moveTo(vx, vy)
                started = true
            } else {
                out.lineTo(vx, vy)
            }
            distance += step
        }
        // Ensure end point
        measure.getPosTan(length, coords, null)
        val (ex, ey) = mmahToView(coords[0], coords[1])
        if (started) out.lineTo(ex, ey) else out.moveTo(ex, ey)
        out.asComposePath()
    } catch (_: Exception) {
        null
    }
}
