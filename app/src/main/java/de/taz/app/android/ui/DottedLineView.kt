package de.taz.app.android.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import de.taz.app.android.R


class DottedLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val DOTTED_LINE_CIRCLE_RADIUS_DP = 1.5f
        const val DOTTED_LINE_SPACING_DP = 8f
    }

    private val path: Path = Path()
    private val circlePath = Path().apply {
        val radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DOTTED_LINE_CIRCLE_RADIUS_DP,
            resources.displayMetrics
        )
        addCircle(0f, 0f, radius, Path.Direction.CW)
    }
    private val circlePathEffect = PathDashPathEffect(
        circlePath,
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DOTTED_LINE_SPACING_DP,
            resources.displayMetrics
        ),
        0f,
        PathDashPathEffect.Style.ROTATE
    )
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val attributeArray = context.obtainStyledAttributes(attrs, R.styleable.DottedLineView)
        try {
            val colorAttr = attributeArray.getColor(R.styleable.DottedLineView_color, Color.BLACK)
            color = colorAttr
            pathEffect = circlePathEffect
        } finally {
            attributeArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        super.onDraw(canvas)
        path.reset()
        path.moveTo(0f, height / 2f)
        path.quadTo(width / 2f, height / 2f, width.toFloat(), height / 2f)

        canvas.drawPath(path, paint)
    }
}