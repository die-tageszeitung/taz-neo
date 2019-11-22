package de.taz.app.android.ui.archive.item

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.R
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.DateHelper
import kotlinx.android.synthetic.main.view_archive_item.view.*


class ArchiveItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), ArchiveItemContract.View {
    val presenter = ArchiveItemPresenter()

    private val dateHelper: DateHelper = DateHelper.getInstance()

    init {
        inflate(context, R.layout.view_archive_item, this)

        clearIssue()
        presenter.attach(this)
        presenter.onViewCreated(null)
    }

    override fun clearIssue() {
        clearDate()
        hideBitmap()
        showProgressBar()
    }

    override fun displayIssue(momentImageBitmap: Bitmap, date: String) {
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

    private fun setDate(date: String) {
        fragment_archive_moment_date.text = dateHelper.stringToLocalizedString(date)
    }


    private fun hideBitmap() {
        fragment_archive_moment_image.visibility = View.GONE
    }

    private fun showBitmap(bitmap: Bitmap) {
        fragment_archive_moment_image.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        fragment_archive_moment_image.background = BitmapDrawable(resources, bitmap)
        hideProgressBar()
    }

    private fun showProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.GONE
    }

    override fun setDimension(dimenstionString: String) {
        (fragment_archive_moment_image_wrapper.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimenstionString
    }

    //TODO: We need to implement this to comply with BaseContract.View although unneeded. The TODO is to refactor these interfaces
    override fun getMainView(): MainContract.View? {
        return null
    }

}