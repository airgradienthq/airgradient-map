package com.airgradient.android.ui.community.Views

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import coil.ImageLoader
import coil.request.ImageRequest

class HtmlTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private val imageLoader = ImageLoader.Builder(context).build()

    init {
        movementMethod = LinkMovementMethod.getInstance()
        setLineSpacing(0f, 1.3f)
    }

    fun setHtml(html: String, textColor: Int, fontSizeSp: Float) {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        val imageGetter = UrlImageGetter()
        val styled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
        } else {
            Html.fromHtml(html, imageGetter, null)
        }
        text = styled
    }

    private inner class UrlDrawable : Drawable() {
        var drawable: Drawable? = null
        override fun draw(canvas: Canvas) { drawable?.draw(canvas) }
        override fun setAlpha(alpha: Int) { drawable?.alpha = alpha }
        override fun getOpacity(): Int = drawable?.opacity ?: android.graphics.PixelFormat.TRANSPARENT
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            drawable?.colorFilter = colorFilter
        }
    }

    private inner class UrlImageGetter : Html.ImageGetter {
        override fun getDrawable(source: String): Drawable {
            val urlDrawable = UrlDrawable()
            val request = ImageRequest.Builder(context)
                .data(source)
                .target { result ->
                    val drawable = result
                    val availableWidth = this@HtmlTextView.width.takeIf { it > 0 } ?: drawable.intrinsicWidth
                    val ratio = if (drawable.intrinsicWidth > 0 && availableWidth > 0) {
                        availableWidth.toFloat() / drawable.intrinsicWidth
                    } else 1f
                    val height = (drawable.intrinsicHeight * ratio).toInt().coerceAtLeast(1)
                    drawable.setBounds(0, 0, availableWidth, height)
                    urlDrawable.drawable = drawable
                    urlDrawable.setBounds(0, 0, availableWidth, height)
                    this@HtmlTextView.text = this@HtmlTextView.text
                    invalidate()
                    requestLayout()
                }
                .build()
            imageLoader.enqueue(request)
            return urlDrawable
        }
    }
}
