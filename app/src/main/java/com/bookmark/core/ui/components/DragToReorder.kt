package com.bookmark.core.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Minimal drag-to-reorder for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * The gesture lives on each row's drag handle, not on the list. An earlier
 * version put a long-press detector on the LazyColumn itself and it never
 * fired -- the list's own scroll gesture sees pointer events first and wins.
 * A dedicated handle also means an immediate drag (no long-press delay) and
 * leaves the row free to handle taps.
 */
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemOffset by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset = 0

    private val draggingItemInfo: LazyListItemInfo?
        get() = draggingItemIndex?.let { index ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }

    /** Pixel offset to apply to the dragged row while it is in flight. */
    fun offsetFor(index: Int): Float =
        if (index == draggingItemIndex) {
            (draggingItemInitialOffset + draggingItemOffset) - (draggingItemInfo?.offset ?: 0)
        } else {
            0f
        }

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = 0f
        draggingItemInitialOffset = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.offset
            ?: 0
    }

    fun onDrag(deltaY: Float) {
        draggingItemOffset += deltaY
        val current = draggingItemInfo ?: return
        val startOffset = current.offset + offsetFor(current.index)
        val middle = startOffset + current.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            middle.toInt() in candidate.offset..(candidate.offset + candidate.size) &&
                candidate.index != current.index
        } ?: return

        onMove(current.index, target.index)
        draggingItemIndex = target.index
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
        draggingItemInitialOffset = 0
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): DragDropState = remember(listState) { DragDropState(listState, onMove) }

/**
 * Attach to a row's drag handle. [index] is the row's position in the list the
 * user currently sees, so it must come from the same list that is rendered.
 *
 * Note what `pointerInput` is keyed on: [state] only, never [index]. Reordering
 * changes every row's index, and keying on it restarts the pointer handler
 * mid-gesture -- the rows still moved under the finger, but `onDragEnd` never
 * fired and the new order was never written. The index is read through
 * [rememberUpdatedState] so the running gesture always sees the current value.
 */
@Composable
fun Modifier.dragHandle(
    state: DragDropState,
    index: Int,
    onDragEnd: () -> Unit,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    return this.pointerInput(state) {
        detectDragGestures(
            onDragStart = { state.onDragStart(currentIndex) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = {
                state.onDragInterrupted()
                currentOnDragEnd()
            },
            onDragCancel = { state.onDragInterrupted() },
        )
    }
}
