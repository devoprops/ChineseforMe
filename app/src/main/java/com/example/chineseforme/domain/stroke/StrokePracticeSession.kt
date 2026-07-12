package com.example.chineseforme.domain.stroke

import com.example.chineseforme.data.stroke.CharacterStrokeData
import com.example.chineseforme.data.stroke.mmahToView
import kotlin.math.hypot
import kotlin.math.min

/** Guide dim levels: 0 = clearest outlines, [MAX_DIM_LEVEL] = nearly invisible. */
const val STROKE_MAX_DIM_LEVEL = 4

data class StrokePracticeState(
    val character: Char,
    val strokeCount: Int,
    val completedStrokeCount: Int,
    val hintsRemaining: Int,
    val mistakes: Int = 0,
    val allowedMistakes: Int = 3,
    /** 0..[STROKE_MAX_DIM_LEVEL] — how faint unfinished guide strokes are. */
    val guideDimLevel: Int = 0,
    val hintFlashStrokeIndex: Int? = null,
    val failed: Boolean = false,
    val complete: Boolean = false,
    val statusMessage: String? = null
)

/**
 * Quiz against Hanzi Writer medians. User strokes are in MMAH view space (0–1024, y down).
 */
class StrokePracticeSession {
    private var data: CharacterStrokeData? = null
    private var hintsPerAttempt: Int = 3
    private var hintsRemaining: Int = 3
    private var allowedMistakes: Int = 3
    private var mistakes: Int = 0
    private var guideDimLevel: Int = 0
    private var completed: Int = 0
    private var failed: Boolean = false
    private var complete: Boolean = false
    private var hintFlash: Int? = null
    private var status: String? = null

    fun start(
        data: CharacterStrokeData,
        hintsPerAttempt: Int,
        allowedMistakes: Int,
        guideDimLevel: Int = 0
    ): StrokePracticeState {
        this.data = data
        this.hintsPerAttempt = hintsPerAttempt.coerceIn(0, 10)
        this.allowedMistakes = allowedMistakes.coerceIn(0, 20)
        this.guideDimLevel = guideDimLevel.coerceIn(0, STROKE_MAX_DIM_LEVEL)
        return resetAttemptInternal(statusMessage = null)
    }

    fun current(): StrokePracticeState = snapshot()

    fun submitStroke(userPointsView: List<Pair<Float, Float>>): StrokePracticeState {
        val d = data ?: return snapshot()
        if (failed || complete) return snapshot()
        if (userPointsView.size < 2) {
            status = "Stroke too short"
            return snapshot()
        }
        val median = d.medians.getOrNull(completed) ?: return snapshot()
        val medianView = median.map { (x, y) -> mmahToView(x, y) }
        val ok = matchesMedian(userPointsView, medianView)
        return if (ok) {
            completed += 1
            hintFlash = null
            if (completed >= d.strokeCount) {
                complete = true
                status = "Character complete"
            } else {
                status = null
            }
            snapshot()
        } else {
            mistakes += 1
            hintFlash = null
            if (mistakes > allowedMistakes) {
                failed = true
                status = "Too many mistakes — attempt failed"
            } else {
                status = "Incorrect (${mistakes}/${allowedMistakes} mistakes used)"
            }
            snapshot()
        }
    }

    /** Flash next stroke; exhausting hints fails the attempt. */
    fun hint(): StrokePracticeState {
        if (failed || complete) return snapshot()
        if (hintsRemaining <= 0) {
            failed = true
            hintFlash = null
            status = "Hints exhausted — attempt failed"
            return snapshot()
        }
        hintsRemaining -= 1
        hintFlash = completed
        status = if (hintsRemaining == 0) "Last hint used" else null
        return snapshot()
    }

    fun clearHintFlash(): StrokePracticeState {
        hintFlash = null
        return snapshot()
    }

    /** Clear strokes; keep current guide dim level. */
    fun retrySameLevel(): StrokePracticeState =
        resetAttemptInternal(statusMessage = null)

    /**
     * Clear strokes and increase guide dim by one step (harder outlines).
     * Caps at [STROKE_MAX_DIM_LEVEL].
     */
    fun advanceDimAndRetry(): StrokePracticeState {
        guideDimLevel = (guideDimLevel + 1).coerceAtMost(STROKE_MAX_DIM_LEVEL)
        return resetAttemptInternal(
            statusMessage = "Guide dim level ${guideDimLevel + 1}/${STROKE_MAX_DIM_LEVEL + 1}"
        )
    }

    fun resetAttempt(): StrokePracticeState = retrySameLevel()

    private fun resetAttemptInternal(statusMessage: String?): StrokePracticeState {
        val d = data ?: return snapshot()
        hintsRemaining = hintsPerAttempt
        mistakes = 0
        completed = 0
        failed = false
        complete = d.strokeCount == 0
        hintFlash = null
        status = statusMessage
        return snapshot()
    }

    private fun snapshot() = StrokePracticeState(
        character = data?.character ?: ' ',
        strokeCount = data?.strokeCount ?: 0,
        completedStrokeCount = completed,
        hintsRemaining = hintsRemaining,
        mistakes = mistakes,
        allowedMistakes = allowedMistakes,
        guideDimLevel = guideDimLevel,
        hintFlashStrokeIndex = hintFlash,
        failed = failed,
        complete = complete,
        statusMessage = status
    )

    companion object {
        /** Average distance (in 1024 space) from user samples to median polyline. */
        private const val AVG_DIST_THRESHOLD = 55f
        private const val END_DIST_THRESHOLD = 90f

        /** Alpha for unfinished guide strokes at each dim level. */
        fun guideAlphaForDimLevel(level: Int): Float = when (level.coerceIn(0, STROKE_MAX_DIM_LEVEL)) {
            0 -> 0.42f
            1 -> 0.30f
            2 -> 0.18f
            3 -> 0.10f
            else -> 0.04f
        }

        fun matchesMedian(
            user: List<Pair<Float, Float>>,
            median: List<Pair<Float, Float>>
        ): Boolean {
            if (median.size < 2 || user.size < 2) return false
            val startDist = dist(user.first(), median.first())
            val endDist = dist(user.last(), median.last())
            if (startDist > END_DIST_THRESHOLD || endDist > END_DIST_THRESHOLD) return false
            var total = 0f
            val samples = resample(user, 20)
            for (p in samples) {
                total += distanceToPolyline(p, median)
            }
            val avg = total / samples.size
            return avg <= AVG_DIST_THRESHOLD
        }

        private fun resample(
            points: List<Pair<Float, Float>>,
            count: Int
        ): List<Pair<Float, Float>> {
            if (points.size <= count) return points
            val out = ArrayList<Pair<Float, Float>>(count)
            for (i in 0 until count) {
                val t = i.toFloat() / (count - 1).coerceAtLeast(1)
                val idx = (t * (points.lastIndex)).toInt().coerceIn(0, points.lastIndex)
                out.add(points[idx])
            }
            return out
        }

        private fun distanceToPolyline(
            p: Pair<Float, Float>,
            line: List<Pair<Float, Float>>
        ): Float {
            var best = Float.MAX_VALUE
            for (i in 0 until line.lastIndex) {
                best = min(best, distToSegment(p, line[i], line[i + 1]))
            }
            return best
        }

        private fun distToSegment(
            p: Pair<Float, Float>,
            a: Pair<Float, Float>,
            b: Pair<Float, Float>
        ): Float {
            val abx = b.first - a.first
            val aby = b.second - a.second
            val apx = p.first - a.first
            val apy = p.second - a.second
            val abLen2 = abx * abx + aby * aby
            if (abLen2 < 1e-3f) return dist(p, a)
            val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0f, 1f)
            val proj = (a.first + t * abx) to (a.second + t * aby)
            return dist(p, proj)
        }

        private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
            hypot(a.first - b.first, a.second - b.second)
    }
}
