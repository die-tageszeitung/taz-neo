package de.taz.app.android.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import com.google.android.material.textview.MaterialTextView

class NoPaddingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : MaterialTextView(context, attrs, defStyleAttr) {
    private val mPaint: Paint by lazy { paint }
    private val rect = Rect()

    override fun onDraw(canvas: Canvas) {
        val text = calculateTextParams()
        val left: Int = rect.left
        val bottom: Int = rect.bottom
        rect.offset(-rect.left, -rect.top)
        mPaint.isAntiAlias = true
        mPaint.color = currentTextColor
        canvas.drawText(text, (-left).toFloat(), (rect.bottom - bottom).toFloat(), mPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        calculateTextParams()
        setMeasuredDimension(rect.right - rect.left, -rect.top + rect.bottom)
    }

    private fun calculateTextParams(): String {
        text.toString().let {
            val textLength = text.length
            mPaint.textSize = textSize
            mPaint.getTextBounds(it, 0, textLength, rect)
            if (textLength == 0) {
                rect.right = rect.left
            }
            return it
        }
    }
}