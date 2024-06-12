package de.taz.app.android.ui.webview.pager

import androidx.annotation.DrawableRes
import de.taz.app.android.api.models.ArticleStubWithSectionKey

sealed class ArticlePagerItem {

    class ArticleRepresentation(val art: ArticleStubWithSectionKey) : ArticlePagerItem()
    class Tom(@DrawableRes val tomResId: Int) : ArticlePagerItem()
}