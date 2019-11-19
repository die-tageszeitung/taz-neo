package de.taz.app.android.ui.archive.item

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.R
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.DateHelper


class ArchiveItemView : RelativeLayout, ArchiveItemContract.View {
    val presenter = ArchiveItemPresenter()

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet
    ): super(context, attrs)

    constructor(
        context: Context
    ): super(context)

    private val dateHelper: DateHelper = DateHelper.getInstance()

    private var momentImageWrapper: ConstraintLayout
    private var momentImage: ImageView
    private var progressBar: ProgressBar
    private var dateText: TextView

    init {
        inflate(context, R.layout.fragment_archive_item, this)
        momentImageWrapper = findViewById(R.id.fragment_archive_moment_image_wrapper)
        momentImage = findViewById(R.id.fragment_archive_moment_image)
        progressBar = findViewById(R.id.fragment_archive_moment_image_progressbar)
        dateText = findViewById(R.id.fragment_archive_moment_date)
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
        dateText.text = ""
    }

    private fun setDate(date: String) {
        dateText.text = dateHelper.stringToLocalizedString(date)
    }


    private fun hideBitmap() {
        momentImage.visibility = View.GONE
    }

    private fun showBitmap(bitmap: Bitmap) {
        momentImage.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        hideProgressBar()
    }

    private fun showProgressBar() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        progressBar.visibility = View.GONE
    }

    override fun setDimension(dimenstionString: String) {
        (momentImageWrapper.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimenstionString
    }

    //TODO: We need to implement this to comply with BaseContract.View although unneeded. The TODO is to refactor these interfaces
    override fun getMainView(): MainContract.View? {
        return null
    }

}