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
        const val DOT_SIZE_PX = 4.07625f
        const val DOT_TO_SPACE_RELATION = 4.9f
        const val MAX_DOT_CIRCLE_RADIUS = 1.7f
    }

//    The dot radius needs to be depending on screen density, but for tablets ths radius should
//    not be bigger than the specifed value
    private var dottedLineCircleRadiusDp =
        (DOT_SIZE_PX / context.resources.displayMetrics.density).coerceAtMost(MAX_DOT_CIRCLE_RADIUS)

    private val dottedLineSpacingDp = dottedLineCircleRadiusDp * DOT_TO_SPACE_RELATION

    private val path: Path = Path()
    private val circlePath = Path().apply {
        val radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dottedLineCircleRadiusDp,
            resources.displayMetrics
        )
        addCircle(0f, 0f, radius, Path.Direction.CW)
    }
    private val circlePathEffect = PathDashPathEffect(
        circlePath,
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dottedLineSpacingDp,
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