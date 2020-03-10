package de.taz.app.android.ui.moment

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_archive_item.view.*


class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), MomentViewContract.View {
    val presenter = MomentViewPresenter()

    private val log by Log

    private val dateHelper: DateHelper = DateHelper.getInstance()

    init {
        inflate(context, R.layout.view_archive_item, this)

        clearIssue()

        attrs?.let {
            val ta = getContext().obtainStyledAttributes(attrs, R.styleable.MomentView)
            val textColor = ta.getColor(
                R.styleable.MomentView_archive_item_text_color,
                Color.WHITE
            )
            val textAlign = ta.getInteger(
                R.styleable.MomentView_archive_item_text_orientation,
                View.TEXT_ALIGNMENT_CENTER
            )
            val isMomentInDrawer =  ta.getBoolean(
                R.styleable.MomentView_in_drawer_sections,
                false
            )
            if (isMomentInDrawer) {
                fragment_archive_moment_is_downloaded.visibility = View.GONE
            }
            ta.recycle()

            fragment_archive_moment_date?.apply {
                setTextColor(textColor)
                textAlignment = textAlign
            }
        }

        presenter.attach(this)
        presenter.onViewCreated(null)
    }

    override fun clearIssue() {
        clearDate()
        hideBitmap()
        showProgressBar()
    }

    override fun displayIssue(momentImageBitmap: Bitmap, date: String?) {
        hideProgressBar()
        showBitmap(momentImageBitmap)
        setDate(date)
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        var context = context
        while (context !is LifecycleOwner) {
            context = (context as ContextWrapper).baseContext
        }
        return context
    }

    private fun clearDate() {
        fragment_archive_moment_date.text = ""
    }

    private fun setDate(date: String?) {
        if (date !== null) {
            fragment_archive_moment_date.text = dateHelper.stringToLongLocalizedString(date)
        }
        else {
            fragment_archive_moment_date.visibility = View.GONE
        }
    }


    private fun hideBitmap() {
        fragment_archive_moment_image.visibility = View.INVISIBLE
    }

    private fun showBitmap(bitmap: Bitmap) {
        fragment_archive_moment_image.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        hideProgressBar()
    }

    private fun showProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.GONE
    }

    private fun setDimension(dimensionString: String) {
        log.info("setting dimension to $dimensionString")
        fragment_archive_item_centered.apply {
            (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
            requestLayout()
            forceLayout()
        }

    }

    override fun showDownloadIcon() {
        fragment_archive_moment_is_downloaded?.visibility = View.VISIBLE
    }

    override fun hideDownloadIcon() {
        fragment_archive_moment_is_downloaded?.visibility = View.INVISIBLE
    }

    override fun setDimension(feed: Feed?) {
        setDimension(feed?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO)
    }

    //TODO: We need to implement this to comply with BaseContract.View although unneeded. The TODO is to refactor these interfaces
    override fun getMainView(): MainContract.View? {
        return null
    }

}