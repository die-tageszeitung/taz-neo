package de.taz.app.android.coachMarks

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.updateLayoutParams
import de.taz.app.android.R

class PdfDrawerListMomentCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_moment_list
) {

    companion object {
        fun create(
            menuItem: View,
        ) = PdfDrawerListMomentCoachMark().apply {
            this.resizeIcon = true
            this.menuItem = menuItem
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.coach_mark_pdf_drawer_moment_image)?.setImageBitmap(
            (this.menuItem as ImageView).drawable.toBitmap()
        )
    }
}

class PdfDrawerSwitchViewToPagesCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_switch_view_to_pages
) {
    companion object {
        fun create(menuItem: View) = PdfDrawerSwitchViewToPagesCoachMark().apply {
            this.menuItem = menuItem
            this.verticalBias = 0.48f
        }
    }
}

class PdfDrawerGoToSectionCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_list_section
) {
    companion object {
        fun create(menuItem: TextView) = PdfDrawerGoToSectionCoachMark().apply {
            this.menuItem = menuItem
            this.textString = menuItem.text.toString()
            this.useShortArrow = true
        }
    }
}

class PdfDrawerGoToArticleCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_list_article
) {
    private var titleWidth: Int? = null
    private var textString2: String? = null
    private var textString3: String? = null
    private var textString4: String? = null

    companion object {
        fun create(
            menuItem: View,
            title: String,
            teaser: String?,
            author: String?,
            min: String?,
            titleWidth: Int?
        ) =
            PdfDrawerGoToArticleCoachMark().apply {
                this.menuItem = menuItem
                this.textString = title
                this.textString2 = teaser
                this.textString3 = author
                this.textString4 = min
                this.verticalBias = 0.65f
                this.titleWidth = titleWidth
            }
    }

    override fun onCoachMarkCreated() {
        titleWidth?.let {
            view?.findViewById<TextView>(R.id.coach_mark_text_view)?.width = titleWidth!!
        }
        textString2?.let {
            view?.findViewById<TextView>(R.id.coach_mark_text_view_2)?.text = it
        }
        textString3?.let {
            view?.findViewById<TextView>(R.id.coach_mark_text_view_3)?.text = it
        }
        textString4?.let {
            view?.findViewById<TextView>(R.id.coach_mark_text_view_4)?.text = it
        }
        this.menuItem?.width?.let {
            view?.findViewById<View>(R.id.coach_mark_text_wrapper)
                ?.updateLayoutParams { width = it }
        }

        super.onCoachMarkCreated()
    }
}

class PdfDrawerGoToPageCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_list_page
) {
    companion object {
        fun create(menuItem: View) = PdfDrawerGoToPageCoachMark().apply {
            this.menuItem = menuItem
            this.useShortArrow = true
            this.verticalBias = 0.7f
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.coach_mark_pdf_drawer_moment_image)?.setImageBitmap(
            (this.menuItem as ImageView).drawable.toBitmap()
        )
    }
}
