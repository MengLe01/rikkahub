package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HorizontalDirectionRatio = 1.732f
private const val DrawerAnimationDurationMillis = 256
private val DrawerFlingThreshold = 400.dp

@Stable
internal class ChatDrawerState(initialValue: DrawerValue) {
    private val animation = Animatable(if (initialValue == DrawerValue.Open) 1f else 0f)
    private var dragProgress by mutableStateOf<Float?>(null)

    val progress: Float
        get() = dragProgress ?: animation.value

    val isVisible: Boolean
        get() = progress > 0f

    suspend fun open() {
        animateTo(DrawerValue.Open)
    }

    suspend fun close() {
        animateTo(DrawerValue.Closed)
    }

    internal fun beginInteraction(): Boolean {
        val interrupted = dragProgress != null || animation.isRunning
        if (dragProgress == null && animation.isRunning) {
            dragProgress = animation.value
        }
        return interrupted
    }

    internal fun dragBy(delta: Float, drawerWidth: Float) {
        if (drawerWidth <= 0f) return
        dragProgress = (progress + delta / drawerWidth).coerceIn(0f, 1f)
    }

    internal suspend fun settle(velocity: Float, velocityThreshold: Float) {
        val targetValue = when {
            velocity > velocityThreshold -> DrawerValue.Open
            velocity < -velocityThreshold -> DrawerValue.Closed
            progress >= 0.5f -> DrawerValue.Open
            else -> DrawerValue.Closed
        }
        animateTo(targetValue)
    }

    private suspend fun animateTo(targetValue: DrawerValue) {
        synchronizeAnimationWithProgress()
        val targetProgress = if (targetValue == DrawerValue.Open) 1f else 0f
        animation.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = DrawerAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    private suspend fun synchronizeAnimationWithProgress() {
        val currentProgress = progress
        animation.stop()
        animation.snapTo(currentProgress)
        dragProgress = null
    }

    internal companion object {
        val Saver = Saver<ChatDrawerState, String>(
            save = { state ->
                if (state.progress >= 0.5f) DrawerValue.Open.name else DrawerValue.Closed.name
            },
            restore = { value -> ChatDrawerState(DrawerValue.valueOf(value)) },
        )
    }
}

@Composable
internal fun rememberChatDrawerState(initialValue: DrawerValue): ChatDrawerState = rememberSaveable(
    saver = ChatDrawerState.Saver,
) {
    ChatDrawerState(initialValue)
}

@Composable
internal fun ChatModalNavigationDrawer(
    drawerState: ChatDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val settleScope = rememberCoroutineScope()
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val velocityThreshold = with(density) { DrawerFlingThreshold.toPx() }
    val drawerWidth = with(density) { ChatDrawerWidth.toPx() }
    val progress = drawerState.progress

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawerState, drawerWidth, layoutDirection, velocityThreshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val velocityTracker = VelocityTracker()
                    var totalDelta = Offset.Zero
                    var direction = GestureDirection.Undecided
                    val interruptedAnimation = drawerState.beginInteraction()

                    // Freeze a running settle immediately, then decide the axis exactly once after touch slop.
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            if (direction == GestureDirection.Horizontal || interruptedAnimation) {
                                settleScope.launch {
                                    drawerState.settle(0f, velocityThreshold)
                                }
                            }
                            break
                        }

                        val delta = change.position - change.previousPosition
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (change.pressed) {
                            totalDelta += delta
                            when (direction) {
                                GestureDirection.Undecided -> {
                                    if (totalDelta.getDistance() > viewConfiguration.touchSlop) {
                                        direction = if (
                                            abs(totalDelta.x) > abs(totalDelta.y) * HorizontalDirectionRatio
                                        ) {
                                            GestureDirection.Horizontal
                                        } else {
                                            GestureDirection.Vertical
                                        }

                                        if (direction == GestureDirection.Horizontal) {
                                            drawerState.dragBy(
                                                delta = totalDelta.x.toDrawerDelta(layoutDirection),
                                                drawerWidth = drawerWidth,
                                            )
                                            change.consume()
                                        }
                                    }
                                }

                                GestureDirection.Horizontal -> {
                                    drawerState.dragBy(
                                        delta = delta.x.toDrawerDelta(layoutDirection),
                                        drawerWidth = drawerWidth,
                                    )
                                    change.consume()
                                }

                                GestureDirection.Vertical -> Unit
                            }
                        } else {
                            if (direction == GestureDirection.Horizontal) {
                                change.consume()
                                val velocity = velocityTracker.calculateVelocity().x
                                    .toDrawerDelta(layoutDirection)
                                settleScope.launch {
                                    drawerState.settle(velocity, velocityThreshold)
                                }
                            } else if (interruptedAnimation) {
                                settleScope.launch {
                                    drawerState.settle(0f, velocityThreshold)
                                }
                            }
                            break
                        }
                    }
                }
            },
    ) {
        content()

        if (progress > 0f) {
            val scrimColor = DrawerDefaults.scrimColor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * progress))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClickLabel = "Close navigation drawer",
                    ) {
                        settleScope.launch { drawerState.close() }
                    },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .align(
                    if (layoutDirection == LayoutDirection.Ltr) {
                        Alignment.TopStart
                    } else {
                        Alignment.TopEnd
                    },
                )
                .offset {
                    val hiddenDistance = drawerWidth * (1f - drawerState.progress)
                    IntOffset(
                        x = if (layoutDirection == LayoutDirection.Ltr) {
                            -hiddenDistance.roundToInt()
                        } else {
                            hiddenDistance.roundToInt()
                        },
                        y = 0,
                    )
                },
        ) {
            drawerContent()
        }
    }
}

private enum class GestureDirection {
    Undecided,
    Horizontal,
    Vertical,
}

private fun Float.toDrawerDelta(layoutDirection: LayoutDirection): Float =
    if (layoutDirection == LayoutDirection.Ltr) this else -this

