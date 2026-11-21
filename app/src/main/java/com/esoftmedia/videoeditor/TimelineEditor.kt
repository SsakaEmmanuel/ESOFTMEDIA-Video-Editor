package com.esoftmedia.videoeditor

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Non-destructive timeline model. sourceInMs/sourceOutMs always refer to the
 * original media file; edits never rewrite the imported source.
 */
data class TimelineClip(
    val id: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val sourceInMs: Long = 0L,
    val sourceOutMs: Long = sourceDurationMs,
    val playbackSpeed: Float = 1f
) {
    init {
        require(id.isNotBlank()) { "Clip id must not be blank" }
        require(sourceUri.isNotBlank()) { "Source URI must not be blank" }
        require(sourceDurationMs > 0L) { "Source duration must be positive" }
        require(sourceInMs >= 0L && sourceOutMs <= sourceDurationMs && sourceInMs < sourceOutMs) {
            "Trim points must be within the source and start before end"
        }
        require(playbackSpeed in 0.25f..4f) { "Playback speed must be between 0.25x and 4x" }
    }

    val sourceLengthMs: Long get() = sourceOutMs - sourceInMs
    val timelineDurationMs: Long get() = (sourceLengthMs / playbackSpeed).roundToLong()
}

data class AudioTimelineClip(
    val id: String,
    val sourceUri: String,
    val startAtMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    init {
        require(startAtMs >= 0L && sourceInMs >= 0L && sourceOutMs > sourceInMs)
        require(volume in 0f..2f)
        require(fadeInMs >= 0L && fadeOutMs >= 0L)
    }
}

data class TextOverlay(
    val id: String,
    val text: String,
    val startAtMs: Long,
    val endAtMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f
) {
    init {
        require(id.isNotBlank() && text.isNotBlank())
        require(startAtMs >= 0L && endAtMs > startAtMs)
        require(x in 0f..1f && y in 0f..1f && scale > 0f)
    }
}

data class TimelineState(
    val clips: List<TimelineClip> = emptyList(),
    val playheadMs: Long = 0L,
    val zoom: Float = 1f
) {
    val durationMs: Long get() = clips.sumOf { it.timelineDurationMs }
    val safePlayheadMs: Long get() = playheadMs.coerceIn(0L, durationMs)

    fun clipStartMs(index: Int): Long {
        require(index in clips.indices)
        return clips.take(index).sumOf { it.timelineDurationMs }
    }
}

/** Pure timeline operations, separated from Compose so they can be unit-tested. */
object TimelineEditor {
    fun trim(state: TimelineState, clipId: String, sourceInMs: Long, sourceOutMs: Long): TimelineState {
        val index = state.clips.indexOfFirst { it.id == clipId }
        require(index >= 0) { "Clip not found" }
        val clip = state.clips[index]
        val updated = clip.copy(sourceInMs = sourceInMs, sourceOutMs = sourceOutMs)
        return state.copy(clips = state.clips.toMutableList().also { it[index] = updated },
            playheadMs = state.safePlayheadMs)
    }

    /** Split the selected clip at the global timeline playhead. */
    fun splitAtPlayhead(state: TimelineState, clipId: String): TimelineState {
        val index = state.clips.indexOfFirst { it.id == clipId }
        require(index >= 0) { "Clip not found" }
        val clip = state.clips[index]
        val clipStart = state.clipStartMs(index)
        val localTimelinePosition = state.safePlayheadMs - clipStart
        require(localTimelinePosition > 0L && localTimelinePosition < clip.timelineDurationMs) {
            "Playhead must be inside the selected clip"
        }
        val sourceSplit = (clip.sourceInMs + localTimelinePosition * clip.playbackSpeed)
            .roundToLong()
            .coerceIn(clip.sourceInMs + 1L, clip.sourceOutMs - 1L)
        val left = clip.copy(sourceOutMs = sourceSplit)
        val rightIdBase = clip.id + "-split"
        var rightId = rightIdBase
        var suffix = 2
        while (state.clips.any { it.id == rightId }) {
            rightId = rightIdBase + "-" + suffix
            suffix++
        }
        val right = clip.copy(id = rightId, sourceInMs = sourceSplit)
        val updated = state.clips.toMutableList()
        updated[index] = left
        updated.add(index + 1, right)
        return state.copy(clips = updated)
    }

    fun move(state: TimelineState, fromIndex: Int, toIndex: Int): TimelineState {
        require(fromIndex in state.clips.indices && toIndex in state.clips.indices)
        val updated = state.clips.toMutableList()
        val moved = updated.removeAt(fromIndex)
        updated.add(toIndex, moved)
        return state.copy(clips = updated)
    }

    fun duplicate(state: TimelineState, clipId: String): TimelineState {
        val index = state.clips.indexOfFirst { it.id == clipId }
        require(index >= 0) { "Clip not found" }
        val source = state.clips[index]
        val base = source.id + "-copy"
        var newId = base
        var suffix = 2
        while (state.clips.any { it.id == newId }) {
            newId = base + "-" + suffix
            suffix++
        }
        val updated = state.clips.toMutableList()
        updated.add(index + 1, source.copy(id = newId))
        return state.copy(clips = updated)
    }

    fun remove(state: TimelineState, clipId: String): TimelineState =
        state.copy(clips = state.clips.filterNot { it.id == clipId },
            playheadMs = state.playheadMs.coerceAtMost(state.clips.filterNot { it.id == clipId }.sumOf { it.timelineDurationMs }))

    /** Snap to the nearest marker only when it falls inside the supplied tolerance. */
    fun snap(positionMs: Long, markersMs: List<Long>, toleranceMs: Long = 120L): Long {
        require(toleranceMs >= 0L)
        val nearest = markersMs.minByOrNull { abs(it - positionMs) } ?: return positionMs
        return if (abs(nearest - positionMs) <= toleranceMs) nearest else positionMs
    }
}

/** Small command history for undo/redo of project/timeline state changes. */
class TimelineHistory(initial: TimelineState = TimelineState()) {
    private val undoStates = mutableListOf<TimelineState>()
    private val redoStates = mutableListOf<TimelineState>()
    var current: TimelineState = initial
        private set

    val canUndo: Boolean get() = undoStates.isNotEmpty()
    val canRedo: Boolean get() = redoStates.isNotEmpty()

    fun commit(next: TimelineState) {
        if (next == current) return
        undoStates.add(current)
        current = next
        redoStates.clear()
    }

    fun undo(): TimelineState {
        if (canUndo) {
            redoStates.add(current)
            current = undoStates.removeAt(undoStates.lastIndex)
        }
        return current
    }

    fun redo(): TimelineState {
        if (canRedo) {
            undoStates.add(current)
            current = redoStates.removeAt(redoStates.lastIndex)
        }
        return current
    }
}
