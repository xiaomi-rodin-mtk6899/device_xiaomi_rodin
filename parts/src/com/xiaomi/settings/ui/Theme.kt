/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

// ──────────────────────────────────────────────────────────────────────────
// Lifts a Color's lightness in LAB space by [amount] (0f..1f).
// Keeps hue + chroma 100% from the dynamic scheme — no hardcoded tints.
// ──────────────────────────────────────────────────────────────────────────

private fun Color.liftLightness(amount: Float): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(this.toArgb(), lab)
    lab[0] = (lab[0] + amount * 100.0).coerceIn(0.0, 100.0)
    return Color(ColorUtils.LABToColor(lab[0], lab[1], lab[2]))
}

// ──────────────────────────────────────────────────────────────────────────
// Theme entry point
// ──────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme  = darkColorScheme()

// Material Expressive 3 shape tokens — generous radii for the
// expressive "pill + sheet" language used across the UI.
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun XiaomiPartsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content:   @Composable () -> Unit,
) {
    val context      = LocalContext.current
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseScheme = when {
        dynamicColor && darkTheme  -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme                  -> DarkColorScheme
        else                       -> LightColorScheme
    }

    // In dark mode, lift background/surface layers so they match the
    // system Settings brightness (~tone 6→10→12→17→22 in M3 tonal palette).
    // All hue/chroma comes from the dynamic scheme itself — nothing hardcoded.
    val colorScheme = if (darkTheme) {
        baseScheme.copy(
            background              = baseScheme.background.liftLightness(0.06f),
            surface                 = baseScheme.surface.liftLightness(0.06f),
            surfaceContainer        = baseScheme.surfaceContainer.liftLightness(0.06f),
            surfaceContainerLow     = baseScheme.surfaceContainerLow.liftLightness(0.06f),
            surfaceContainerHigh    = baseScheme.surfaceContainerHigh.liftLightness(0.06f),
            surfaceContainerHighest = baseScheme.surfaceContainerHighest.liftLightness(0.06f),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        shapes      = ExpressiveShapes,
        content     = content,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Motion engine — M3 Expressive motion tokens
// ──────────────────────────────────────────────────────────────────────────
// Emphasized easing curves per the Material Motion spec:
//   • Emphasized-decelerate 0.05, 0.7, 0.1, 1.0   (elements entering)
//   • Emphasized-accelerate 0.3, 0.0, 0.8, 0.15   (elements exiting)
// Springs use low damping for the bouncy "expressive" feel.
// ──────────────────────────────────────────────────────────────────────────

object Motion {
    private val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val StandardEasing: Easing       = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Full-screen navigation: subtle spatial spring with a gentle bounce. */
    fun <T> navSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness    = Spring.StiffnessMediumLow,
    )

    /** Nav fade/scale cross-fade on top of the spatial movement. */
    fun <T> navEffectsSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 200, easing = EmphasizedDecelerate)

    /** Default enter/exit for cards, sheets, banners. */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    )

    /** Staggered entry for list items (fade + small slide). */
    fun <T> itemEnterSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 280, easing = EmphasizedDecelerate)

    /** Quick press feedback on interactive cards. */
    fun <T> pressSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    )

    /** Live value transitions (frequencies, percentages, temps). */
    fun <T> liveValueSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 700, easing = StandardEasing)

    fun <T> shimmerSpec(): DurationBasedAnimationSpec<T> =
        tween(durationMillis = 2800, easing = LinearEasing)

    fun checkFadeInSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = 150, easing = EmphasizedDecelerate)

    fun checkFadeOutSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = 100, easing = EmphasizedAccelerate)
}

// ──────────────────────────────────────────────────────────────────────────
// Shared shapes
// ──────────────────────────────────────────────────────────────────────────

val bottomSheetTopShape: Shape
    @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd   = CornerSize(0.dp),
    )

// ──────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────

const val toastDebounceMs:    Long  = 2_000L
const val shimmerAlpha:       Float = 0.05f
const val blobRadiusFraction: Float = 0.38f
