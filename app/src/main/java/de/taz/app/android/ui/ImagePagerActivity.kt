package de.taz.app.android.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.webview.ImageFragment
import de.taz.app.android.ui.webview.pager.ARTICLE_NAME
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImagePagerActivity : NightModeActivity(R.layout.activity_login)  {

    private lateinit var mPager: ViewPager2
    val log by Log
    var articleName: String? = null
    var imageList: List<Image>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_image_pager)

        articleName = intent.extras?.getString(ARTICLE_NAME)
        lifecycleScope.launch(Dispatchers.IO) {
            imageList = getImageList(articleName)
            /* imageList?.forEach {
                DownloadService.getInstance(applicationContext).download(it, "https://dl.taz.de/data/tApp/taz/content/2020/2020-06-22")
             }*/
        }

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = findViewById(R.id.fragment_image_pager)

        // The pager adapter, which provides the pages to the view pager widget.
        val pagerAdapter = ImagePagerAdapter(this)
        mPager.adapter = pagerAdapter
    }
/*
    override fun onBackPressed() {
        if (mPager.currentItem == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed()
        } else {
            // Otherwise, select the previous step.
            mPager.currentItem = mPager.currentItem - 1
        }
    }*/

    private suspend fun getImageList(articleName: String?): List<Image>? =
        withContext(Dispatchers.IO) {
            articleName?.let {
                ArticleRepository.getInstance().getImagesForArticle(it)
                    .filter { image -> image.resolution == ImageResolution.normal }
            }
        }

    /**
     * A simple pager adapter that represents ImageFragment objects, in
     * sequence.
     */
    private inner class ImagePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

        override fun createFragment(position: Int): Fragment {
            return ImageFragment(imageList?.get(position))
        }

        override fun getItemCount(): Int {
            return imageList?.size ?: 0
        }
    }
}

