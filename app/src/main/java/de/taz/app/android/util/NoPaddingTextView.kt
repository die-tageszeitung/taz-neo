package de.taz.app.android.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import de.taz.app.android.R

class NoPaddingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val mPaint: Paint by lazy { paint }
    private val mBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        val text = calculateTextParams()
        val left = mBounds.left
        val bottom = mBounds.bottom
        mBounds.offset(-mBounds.left, -mBounds.top)
        mPaint.isAntiAlias = true
        mPaint.color = currentTextColor
        canvas.drawText(text, -left.toFloat(), (mBounds.bottom - bottom).toFloat(), mPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        calculateTextParams()
        setMeasuredDimension(mBounds.width() + 1, -mBounds.top + 2)
    }

    private fun calculateTextParams(): String {
        text.toString().let {
            val textLength = text.length
            mPaint.textSize = textSize
            mPaint.getTextBounds(it, 0, textLength, mBounds)
            if (textLength == 0) {
                mBounds.right = mBounds.left
            }
            return it
        }
    }
}