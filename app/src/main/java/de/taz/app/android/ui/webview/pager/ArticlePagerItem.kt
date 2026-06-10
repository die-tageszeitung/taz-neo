package de.taz.app.android.ui.webview.pager

import androidx.annotation.DrawableRes
import de.taz.app.android.api.models.ArticleWithSectionKey

sealed class ArticlePagerItem {

    class ArticleRepresentation(val art: ArticleWithSectionKey) : ArticlePagerItem()
    class Tom(@DrawableRes val tomResId: Int) : ArticlePagerItem()
}