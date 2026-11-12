package com.scalc.calculator.calc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.ui.components.KeyKind
import com.scalc.calculator.ui.components.TextKey
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Expand/collapse animation length; Fluent motion stays short. */
private const val PANEL_ANIMATION_MILLIS = 180

/** Release speed above which a drag settles in its direction regardless of the position. */
private val SettleVelocity = 400.dp

/**
 * Expansion of the scientific keypad, 0 (collapsed) to 1 (expanded). Follows the finger while the
 * handle is dragged, otherwise animates to the ViewModel's [CalculatorUiState.scientificExpanded].
 */
@Stable
internal class ScientificPanelState(expanded: Boolean) {
    private val animatable = Animatable(if (expanded) 1f else 0f)
    private var dragFraction by mutableFloatStateOf(0f)

    var isDragging by mutableStateOf(false)
        private set

    /** Target chosen by the user's last drag while its settle animation runs. */
    private var settlingTo: Boolean? = null

    /** Incremented per drag, so a settle animation never ends a newer drag. */
    private var dragGeneration = 0

    /** Current expansion; read it in layout or draw lambdas to avoid recomposing every frame. */
    val fraction: Float get() = if (isDragging) dragFraction else animatable.value

    fun startDrag() {
        dragFraction = if (isDragging) dragFraction else animatable.value
        isDragging = true
        dragGeneration++
    }

    /**
     * Abandons a drag that never reached [settle] (the handle left composition mid-gesture); the
     * panel then shows the last settled state again.
     */
    fun cancelDrag() {
        if (!isDragging) return
        isDragging = false
        dragGeneration++
    }

    /** Moves by [deltaPx] (positive = down = expand) relative to the fully expanded [fullHeightPx]. */
    fun dragBy(deltaPx: Float, fullHeightPx: Float) {
        if (!isDragging || fullHeightPx <= 0f) return
        dragFraction = (dragFraction + deltaPx / fullHeightPx).coerceIn(0f, 1f)
    }

    /**
     * Decides where a released drag goes: a fast fling follows its direction (down expands),
     * otherwise the panel snaps to the nearer state (50 % threshold).
     */
    fun releaseTarget(velocityPxPerSecond: Float, velocityThresholdPx: Float): Boolean = when {
        abs(velocityPxPerSecond) >= velocityThresholdPx -> velocityPxPerSecond > 0f
        else -> dragFraction >= 0.5f
    }

    /**
     * Animates from the released drag position to [expanded]. If the user grabs the handle again
     * meanwhile, the newer drag keeps control and this animation no longer touches the state.
     */
    suspend fun settle(expanded: Boolean) {
        val generation = dragGeneration
        settlingTo = expanded
        try {
            animatable.snapTo(dragFraction)
            if (generation == dragGeneration) isDragging = false
            animatable.animateTo(if (expanded) 1f else 0f, tween(PANEL_ANIMATION_MILLIS))
        } finally {
            if (generation == dragGeneration) {
                isDragging = false
                if (settlingTo == expanded) settlingTo = null
            }
        }
    }

    /** Follows a ViewModel change (tap, restore); ignored while the user drags or settles there. */
    suspend fun animateTo(expanded: Boolean) {
        if (isDragging || settlingTo == expanded) return
        animatable.animateTo(if (expanded) 1f else 0f, tween(PANEL_ANIMATION_MILLIS))
    }
}

/**
 * Full-width expander strip with a centered 36 x 4 dp bar, placed below the scientific keypad and
 * above the standard keypad. Tap toggles; dragging down expands and dragging up collapses. The
 * strip moves with the panel's bottom edge, so it stays under the finger while dragging and the
 * panel snaps open or closed on release.
 */
@Composable
internal fun ExpanderHandle(
    panel: ScientificPanelState,
    expanded: Boolean,
    fullHeightPx: Int,
    onToggle: () -> Unit,
    onSettled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluentTheme.colors
    val scope = rememberCoroutineScope()
    val fullHeight by rememberUpdatedState(fullHeightPx.toFloat())
    val currentOnSettled by rememberUpdatedState(onSettled)
    val thresholdPx = with(LocalDensity.current) { SettleVelocity.toPx() }
    val dragState = rememberDraggableState { delta -> panel.dragBy(delta, fullHeight) }
    val description = stringResource(R.string.calc_scientific_functions)
    val state = stringResource(if (expanded) R.string.calc_state_expanded else R.string.calc_state_collapsed)
    DisposableEffect(panel) { onDispose { panel.cancelDrag() } }
    Box(
        modifier
            .fillMaxWidth()
            .height(HandleHeight)
            .background(colors.background)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { panel.startDrag() },
                onDragStopped = { velocity ->
                    val target = panel.releaseTarget(velocity, thresholdPx)
                    currentOnSettled(target)
                    scope.launch { panel.settle(target) }
                },
            )
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = description
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    if (panel.isDragging) colors.accent else colors.borderStrong,
                    RoundedCornerShape(FluentDimens.cornerSmall),
                ),
        )
    }
}

/**
 * Hosts the scientific keypad at [fullHeightPx] and shows `fraction` of it, anchored to its bottom
 * edge: the expander handle sits directly below the slot, so while the panel opens the keypad
 * slides down from under the action row together with the handle, like a drawer. The keys are
 * only composed while the panel is at least partly open, so a collapsed panel is invisible to
 * accessibility services and keyboard focus.
 */
@Composable
internal fun ScientificSlot(
    panel: ScientificPanelState,
    fullHeightPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visible by remember(panel) { derivedStateOf { panel.fraction > 0f } }
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val visibleHeight = (fullHeightPx * panel.fraction).roundToInt().coerceIn(0, fullHeightPx)
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, fullHeightPx),
                )
                layout(constraints.maxWidth, visibleHeight) { placeable.place(0, visibleHeight - fullHeightPx) }
            },
    ) {
        if (visible) content()
    }
}

/** One cell of the scientific keypad. */
internal sealed interface SciCell {
    data class Key(val spec: KeySpec) : SciCell
    data object Inverse : SciCell
    data class Angle(val mode: AngleMode) : SciCell
}

/**
 * Arrangement of the 15 scientific keys.
 *
 * Portrait, 5 columns x 3 rows above the standard keypad:
 * ```
 * | sin | cos | tan | log | ln  |      inverse: sin⁻¹ cos⁻¹ tan⁻¹ 10ˣ eˣ
 * | (   | )   | ^   | √   | !   |
 * | π   | e   | inv | RAD | DEG |      the active angle unit is highlighted like inv
 * ```
 * Compact landscape, 3 columns x 5 rows beside the standard keypad, one row per standard row:
 * ```
 * | inv | RAD | DEG |
 * | sin | cos | tan |
 * | ln  | log | !   |
 * | π   | e   | ^   |
 * | (   | )   | √   |
 * ```
 */
internal fun scientificRows(inverse: Boolean, landscape: Boolean): List<List<SciCell>> {
    val (sin, cos, tan, log, ln) = functionRow(inverse).map { SciCell.Key(it) }
    val (open, close, power, root, factorial) = SecondRow.map { SciCell.Key(it) }
    val (pi, euler) = ThirdRowConstants.map { SciCell.Key(it) }
    val rad = SciCell.Angle(AngleMode.RAD)
    val deg = SciCell.Angle(AngleMode.DEG)
    return if (landscape) {
        listOf(
            listOf(SciCell.Inverse, rad, deg),
            listOf(sin, cos, tan),
            listOf(ln, log, factorial),
            listOf(pi, euler, power),
            listOf(open, close, root),
        )
    } else {
        listOf(
            listOf(sin, cos, tan, log, ln),
            listOf(open, close, power, root, factorial),
            listOf(pi, euler, SciCell.Inverse, rad, deg),
        )
    }
}

/**
 * The scientific keypad, filling its bounds: 5 x 3 in portrait (inside the expandable slot) or
 * 3 x 5 in compact [landscape], where it sits left of the standard keypad. In landscape the
 * keypad has no right padding: the standard keypad's own padding provides that gap, so the two
 * blocks are separated by one key gap and their rows line up.
 */
@Composable
internal fun ScientificKeypad(
    inverse: Boolean,
    angleMode: AngleMode,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val gap = FluentDimens.keyGap
    val keyStyle = FluentTheme.typography.keySmall
    val inverseState = stringResource(if (inverse) R.string.calc_state_on else R.string.calc_state_off)
    Column(
        modifier
            .fillMaxSize()
            .background(FluentTheme.colors.keypadGap)
            .then(
                if (landscape) {
                    Modifier.padding(start = gap, top = gap, bottom = gap)
                } else {
                    Modifier.padding(start = gap, top = gap, end = gap)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        scientificRows(inverse, landscape).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell ->
                    val cellModifier = Modifier.weight(1f).fillMaxHeight()
                    when (cell) {
                        is SciCell.Key -> KeyCell(cell.spec, onKey, cellModifier, style = keyStyle)
                        SciCell.Inverse -> KeyCell(
                            spec = InverseKey,
                            onKey = onKey,
                            modifier = cellModifier.semantics {
                                selected = inverse
                                stateDescription = inverseState
                            },
                            style = keyStyle,
                            selected = inverse,
                        )
                        is SciCell.Angle -> AngleKey(cell.mode, angleMode, onKey, cellModifier, keyStyle)
                    }
                }
            }
        }
    }
}

private fun functionRow(inverse: Boolean): List<KeySpec> = if (inverse) InverseFunctionRow else FunctionRow

private fun functionKey(name: String, label: String, description: Int) =
    KeySpec(CalcKey.ScientificFunction(name), label, KeyKind.Function, description)

/** Keys send the plain function name; the ViewModel maps it while inverse mode is on. */
private val FunctionRow = listOf(
    functionKey("sin", "sin", R.string.calc_key_sin),
    functionKey("cos", "cos", R.string.calc_key_cos),
    functionKey("tan", "tan", R.string.calc_key_tan),
    functionKey("log", "log", R.string.calc_key_log),
    functionKey("ln", "ln", R.string.calc_key_ln),
)

private val InverseFunctionRow = listOf(
    functionKey("sin", "sin⁻¹", R.string.calc_key_asin),
    functionKey("cos", "cos⁻¹", R.string.calc_key_acos),
    functionKey("tan", "tan⁻¹", R.string.calc_key_atan),
    functionKey("log", "10ˣ", R.string.calc_key_pow10),
    functionKey("ln", "eˣ", R.string.calc_key_exp),
)

private val SecondRow = listOf(
    KeySpec(CalcKey.OpenParenthesis, "(", KeyKind.Function, R.string.calc_key_open_parenthesis),
    KeySpec(CalcKey.CloseParenthesis, ")", KeyKind.Function, R.string.calc_key_close_parenthesis),
    KeySpec(CalcKey.Operator('^'), "^", KeyKind.Function, R.string.calc_key_power),
    KeySpec(CalcKey.SquareRoot, "√", KeyKind.Function, R.string.calc_key_sqrt),
    KeySpec(CalcKey.Factorial, "!", KeyKind.Function, R.string.calc_key_factorial),
)

private val ThirdRowConstants = listOf(
    KeySpec(CalcKey.Constant('π'), "π", KeyKind.Function, R.string.calc_key_pi),
    KeySpec(CalcKey.Constant('e'), "e", KeyKind.Function, R.string.calc_key_euler),
)

private val InverseKey = KeySpec(CalcKey.Inverse, "inv", KeyKind.Toggle, R.string.calc_key_inverse)

/**
 * One of the two angle-unit keys. The active unit uses the same toggle highlight as inv (accent
 * fill and underline); tapping a unit selects it, tapping the active one keeps it.
 */
@Composable
private fun AngleKey(
    mode: AngleMode,
    current: AngleMode,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier,
    style: TextStyle,
) {
    val active = mode == current
    val label = stringResource(if (mode == AngleMode.RAD) R.string.calc_mode_rad else R.string.calc_mode_deg)
    val description = stringResource(if (mode == AngleMode.RAD) R.string.calc_angle_radians else R.string.calc_angle_degrees)
    TextKey(
        text = label,
        onClick = { onKey(CalcKey.Angle(mode)) },
        modifier = modifier.semantics { selected = active },
        kind = KeyKind.Toggle,
        selected = active,
        style = style,
        contentDescription = description,
    )
}
