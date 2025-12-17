package com.airgradient.android.ui.map.Views.marker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import com.airgradient.android.domain.models.MeasurementType
import kotlin.math.max

private const val BASE_SCALE = 1f
private const val ADD_ANIMATION_START_SCALE = 0.6f
private const val SELECTED_INTRO_SCALE = 2.0f
private const val SELECTED_RESTING_SCALE = 1.6f
private const val INTRO_GROW_DURATION_MS = 260L
private const val INTRO_SETTLE_DURATION_MS = 320L
private const val DESELECTION_DURATION_MS = 200L
private const val PULSE_DURATION_MS = 2000L
private const val PULSE_MIN_SCALE = 0.85f
private const val PULSE_MAX_SCALE = 1.55f
private const val PULSE_START_ALPHA = 0.9f
private const val PULSE_END_ALPHA = 0.1f
private const val HALF_PULSE_OFFSET = 0.5f
private const val SELECTION_SHADOW_OFFSET_MULTIPLIER = 0.35f

internal class CustomMarkerDrawable(
    private var style: MarkerVisualStyle,
    private val onInvalidate: (() -> Unit)? = null
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var currentScale = BASE_SCALE
    private var currentAlpha = 255
    private var pulseProgressA = 0f
    private var pulseProgressB = HALF_PULSE_OFFSET
    private var pulsesEnabled = false
    private var selectionAnimator: AnimatorSet? = null
    private var pulseAnimator: ValueAnimator? = null
    private var deselectionAnimator: Animator? = null
    private var isSelected = false
    private var selectionIntroPlayed = false
    private var selectionShadowEnabled = false
    private val accelerateDecelerate = AccelerateDecelerateInterpolator()
    private val linearInterpolator = LinearInterpolator()

    private val scratchRect = RectF()

    init {
        setBounds(0, 0, style.widthPx, style.heightPx)
        textPaint.textSize = style.textSizePx
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.save()
        canvas.scale(currentScale, currentScale, cx, cy)

        drawGlow(canvas)
        drawBaseShape(canvas)
        drawText(canvas)

        canvas.restore()
        if (pulsesEnabled) {
            drawPulse(canvas, pulseProgressA)
            drawPulse(canvas, pulseProgressB)
        }
    }

    override fun setAlpha(alpha: Int) {
        currentAlpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = currentAlpha

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not supported
    }

    override fun getIntrinsicWidth(): Int = style.widthPx

    override fun getIntrinsicHeight(): Int = style.heightPx

    fun updateStyle(newStyle: MarkerVisualStyle) {
        val requiresRebounds = newStyle.widthPx != style.widthPx || newStyle.heightPx != style.heightPx
        style = newStyle
        textPaint.textSize = style.textSizePx
        if (requiresRebounds) {
            setBounds(0, 0, style.widthPx, style.heightPx)
        }
        invalidateSelf()
    }

    fun animateAddition(duration: Long = 180L, delay: Long = 0L) {
        selectionAnimator?.cancel()
        deselectionAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(ADD_ANIMATION_START_SCALE, BASE_SCALE).apply {
            this.duration = duration
            startDelay = delay
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidateSelf()
                onInvalidate?.invoke()
            }
        }
        animator.start()
    }

    fun animateRemoval(onEnd: () -> Unit) {
        selectionAnimator?.cancel()
        deselectionAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(currentScale, ADD_ANIMATION_START_SCALE)
        animator.duration = 150L
        animator.addUpdateListener {
            currentScale = it.animatedValue as Float
            invalidateSelf()
            onInvalidate?.invoke()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd()
            }
        })
        animator.start()
    }

    fun setSelected(selected: Boolean, animated: Boolean = true) {
        if (selected == isSelected) {
            if (selected && (!pulsesEnabled || !selectionIntroPlayed)) {
                if (animated && selectionAnimator?.isRunning == true) {
                    return
                }
                if (animated) {
                    playSelectionIntroAnimation()
                } else {
                    selectionAnimator?.cancel()
                    currentScale = SELECTED_RESTING_SCALE
                    selectionIntroPlayed = true
                    selectionShadowEnabled = true
                    startPulseAnimation()
                    invalidateSelf()
                }
            }
            return
        }

        isSelected = selected

        if (selected) {
            deselectionAnimator?.cancel()
            selectionIntroPlayed = false
            if (animated) {
                playSelectionIntroAnimation()
            } else {
                selectionAnimator?.cancel()
                currentScale = SELECTED_RESTING_SCALE
                selectionIntroPlayed = true
                selectionShadowEnabled = true
                startPulseAnimation()
                invalidateSelf()
            }
        } else {
            selectionAnimator?.cancel()
            selectionIntroPlayed = false
            stopPulses()
            if (animated) {
                playDeselectionAnimation()
            } else {
                deselectionAnimator?.cancel()
                currentScale = BASE_SCALE
                selectionShadowEnabled = false
                invalidateSelf()
            }
        }
    }

    fun dispose() {
        selectionAnimator?.cancel()
        deselectionAnimator?.cancel()
        selectionShadowEnabled = false
        stopPulses()
        // Prevent leaks and detach from any parent view
        callback = null
    }

    private fun drawBaseShape(canvas: Canvas) {
        applySelectionShadow()
        backgroundPaint.color = style.fillColor
        backgroundPaint.alpha = currentAlpha
        borderPaint.color = style.borderColor
        borderPaint.strokeWidth = style.borderWidthPx
        borderPaint.style = Paint.Style.STROKE
        borderPaint.alpha = (currentAlpha * style.borderAlpha).toInt()

        val inset = style.strokeInsetPx
        scratchRect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )

        if (style.isCluster) {
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), scratchRect.width() / 2f, backgroundPaint)
            if (style.borderWidthPx > 0f) {
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), scratchRect.width() / 2f, borderPaint)
            }
        } else {
            canvas.drawRoundRect(scratchRect, style.cornerRadiusPx, style.cornerRadiusPx, backgroundPaint)
            if (style.borderWidthPx > 0f) {
                canvas.drawRoundRect(scratchRect, style.cornerRadiusPx, style.cornerRadiusPx, borderPaint)
            }
        }
    }

    private fun drawText(canvas: Canvas) {
        textPaint.color = style.textColor
        textPaint.alpha = currentAlpha
        val textY = bounds.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(style.displayText, bounds.exactCenterX(), textY, textPaint)
    }

    private fun drawGlow(canvas: Canvas) {
        if (!style.hasGlow) return
        glowPaint.color = style.glowColor
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        if (style.isCluster) {
            val radius = max(bounds.width(), bounds.height()) * style.glowRadiusMultiplier
            canvas.drawCircle(cx, cy, radius, glowPaint)
        } else {
            val inset = -style.glowExpansionPx
            scratchRect.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset
            )
            val radius = style.cornerRadiusPx + style.glowExpansionPx
            canvas.drawRoundRect(scratchRect, radius, radius, glowPaint)
        }
    }

    private fun drawPulse(canvas: Canvas, progress: Float) {
        if (!pulsesEnabled) return
        val clamped = progress.coerceIn(0f, 1f)
        val alphaFraction = (PULSE_START_ALPHA + (PULSE_END_ALPHA - PULSE_START_ALPHA) * clamped)
            .coerceIn(0f, 1f)
        val alpha = (alphaFraction * 255).toInt().coerceIn(0, 255)
        if (alpha <= 5) return
        pulsePaint.color = style.fillColor
        pulsePaint.alpha = alpha
        pulsePaint.strokeWidth = style.pulseStrokeWidthPx
        val maxDimension = max(bounds.width(), bounds.height()).toFloat()
        val baseRadius = max(style.pulseBaseRadiusPx, maxDimension * 0.5f)
        val scale = PULSE_MIN_SCALE + (PULSE_MAX_SCALE - PULSE_MIN_SCALE) * clamped
        val radius = baseRadius * scale
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, pulsePaint)
    }

    private fun playSelectionIntroAnimation() {
        selectionAnimator?.cancel()
        val introStart = if (currentScale <= 0f) BASE_SCALE else currentScale

        val introUp = ValueAnimator.ofFloat(introStart, SELECTED_INTRO_SCALE).apply {
            duration = INTRO_GROW_DURATION_MS
            interpolator = accelerateDecelerate
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidateSelf()
                onInvalidate?.invoke()
            }
        }
        val settle = ValueAnimator.ofFloat(SELECTED_INTRO_SCALE, SELECTED_RESTING_SCALE).apply {
            duration = INTRO_SETTLE_DURATION_MS
            interpolator = accelerateDecelerate
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidateSelf()
                onInvalidate?.invoke()
            }
        }
        selectionAnimator = AnimatorSet().apply {
            playSequentially(introUp, settle)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    selectionShadowEnabled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    selectionIntroPlayed = true
                    startPulseAnimation()
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (!isSelected) {
                        currentScale = BASE_SCALE
                        selectionShadowEnabled = false
                    } else {
                        currentScale = SELECTED_RESTING_SCALE
                    }
                    invalidateSelf()
                }
            })
            start()
        }
    }

    private fun startPulseAnimation() {
        if (pulsesEnabled) return
        selectionIntroPlayed = true
        selectionShadowEnabled = true
        pulseAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = linearInterpolator
            addUpdateListener {
                pulseProgressA = it.animatedFraction
                pulseProgressB = (pulseProgressA + HALF_PULSE_OFFSET) % 1f
                invalidateSelf()
                onInvalidate?.invoke()
            }
        }
        pulseAnimator = animator
        pulsesEnabled = true
        animator.start()
    }

    private fun playDeselectionAnimation() {
        deselectionAnimator?.cancel()
        selectionShadowEnabled = false
        val animator = ValueAnimator.ofFloat(currentScale, BASE_SCALE).apply {
            duration = DESELECTION_DURATION_MS
            interpolator = accelerateDecelerate
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidateSelf()
                onInvalidate?.invoke()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentScale = BASE_SCALE
                    invalidateSelf()
                }
            })
        }
        deselectionAnimator = animator
        animator.start()
    }

    private fun stopPulses() {
        if (!pulsesEnabled) {
            return
        }
        pulseAnimator?.cancel()
        pulsesEnabled = false
        pulseProgressA = 0f
        pulseProgressB = HALF_PULSE_OFFSET
        invalidateSelf()
    }

    private fun applySelectionShadow() {
        if (selectionShadowEnabled && style.selectionShadowRadiusPx > 0f) {
            backgroundPaint.setShadowLayer(
                style.selectionShadowRadiusPx,
                0f,
                style.selectionShadowRadiusPx * SELECTION_SHADOW_OFFSET_MULTIPLIER,
                style.selectionShadowColor
            )
        } else {
            backgroundPaint.clearShadowLayer()
        }
    }
}

internal data class MarkerVisualStyle(
    val widthPx: Int,
    val heightPx: Int,
    @ColorInt val fillColor: Int,
    @ColorInt val textColor: Int,
    @ColorInt val borderColor: Int,
    @ColorInt val glowColor: Int,
    val borderWidthPx: Float,
    val borderAlpha: Float,
    val displayText: String,
    val textSizePx: Float,
    val isCluster: Boolean,
    val clusterCount: Int?,
    val isReference: Boolean,
    val hasGlow: Boolean,
    val cornerRadiusPx: Float,
    val pulseStrokeWidthPx: Float,
    val measurementType: MeasurementType,
    val strokeInsetPx: Float,
    val glowRadiusMultiplier: Float,
    val glowExpansionPx: Float,
    val pulseBaseRadiusPx: Float,
    val selectionShadowRadiusPx: Float,
    val selectionShadowColor: Int
)
