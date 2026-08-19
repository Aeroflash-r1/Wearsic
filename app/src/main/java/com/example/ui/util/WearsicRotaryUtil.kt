package com.example.ui.util

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState

@Composable
fun Modifier.wearsicRotaryScroll(
    listState: ScalingLazyListState,
    focusRequester: FocusRequester = remember { FocusRequester() }
): Modifier {
    LaunchedEffect(listState) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // Focus request fallback
        }
    }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            listState.dispatchRawDelta(event.verticalScrollPixels)
            true
        }
}
