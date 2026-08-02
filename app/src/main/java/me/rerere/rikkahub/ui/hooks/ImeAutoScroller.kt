package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
    enabled: Boolean,
    isNearEnd: Boolean,
) {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentIsNearEnd by rememberUpdatedState(isNearEnd)

    LaunchedEffect(lazyListState, density) {
        var previousImeHeight = 0
        var wasNearEndBeforeIme = currentIsNearEnd
        var followEndForCurrentImeSession = false

        snapshotFlow {
            Triple(
                ime.getBottom(density),
                currentEnabled,
                currentIsNearEnd,
            )
        }.collect { (imeHeight, enabledNow, isNearEndNow) ->
            if (imeHeight == 0) {
                previousImeHeight = 0
                wasNearEndBeforeIme = isNearEndNow
                followEndForCurrentImeSession = false
                return@collect
            }

            if (previousImeHeight == 0) {
                followEndForCurrentImeSession = enabledNow && wasNearEndBeforeIme
            }
            if (!enabledNow) {
                followEndForCurrentImeSession = false
            }
            previousImeHeight = imeHeight

            if (followEndForCurrentImeSession) {
                val lastItemIndex = lazyListState.layoutInfo.totalItemsCount - 1
                if (lastItemIndex >= 0) {
                    lazyListState.requestScrollToItem(lastItemIndex)
                }
            }
        }
    }
}
