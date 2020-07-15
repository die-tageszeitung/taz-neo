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
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.ui.webview.IMAGE_NAME
import de.taz.app.android.ui.webview.ImageFragment
import de.taz.app.android.ui.webview.pager.DISPLAYABLE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class ImagePagerActivity : NightModeActivity(R.layout.activity_image_pager) {

    private lateinit var mPager: ViewPager2
    private var displayableName: String? = null
    private var imageName: String? = null
    private var imageList: List<Image>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        displayableName = intent.extras?.getString(DISPLAYABLE_NAME)
        imageName = intent.extras?.getString(IMAGE_NAME)

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = findViewById(R.id.activity_image_pager)

        // The pager adapter, which provides the pages to the view pager widget.
        val pagerAdapter = ImagePagerAdapter(this)
        mPager.adapter = pagerAdapter

        lifecycleScope.launch(Dispatchers.IO) {
            imageList = getImageList(displayableName)
            runOnUiThread {
                mPager.setCurrentItem(getPosition(imageName), false)
            }
            imageList?.forEach { it.download(applicationContext) }
        }

        mPager.apply {
            reduceDragSensitivity(6)
            offscreenPageLimit = 2
        }
    }

    private suspend fun getImageList(articleName: String?): List<Image>? =
        withContext(Dispatchers.IO) {
            articleName?.let {
                if (it.startsWith("section.")) {
                    val sectionImages = SectionRepository.getInstance().imagesForSectionStub(it)
                    val image = sectionImages.firstOrNull { image ->
                        image.name == imageName?.replace(
                            "norm",
                            "high"
                        )
                    } ?: sectionImages.firstOrNull { image -> image.name == imageName }
                    image?.let { listOf(image) }
                } else {
                    val articleImages = ArticleRepository.getInstance().getImagesForArticle(it)
                    val highRes =
                        articleImages.filter { image -> image.resolution == ImageResolution.high }
                    val normalRes =
                        articleImages.filter { image -> image.resolution == ImageResolution.normal }
                    normalRes.map { normalResImage ->
                        highRes.firstOrNull { highResImage ->
                            highResImage.name == normalResImage.name.replace(
                                "norm",
                                "high"
                            )
                        } ?: normalResImage
                    }
                }
            }
        }

    private fun getPosition(imageName: String?): Int {
        val highResPosition =
            imageList?.indexOfFirst { it.name == imageName?.replace("norm", "high") }
                ?.coerceAtLeast(0) ?: 0
        val normalResPosition =
            imageList?.indexOfFirst { it.name == imageName }?.coerceAtLeast(0) ?: 0
        return max(highResPosition, normalResPosition)
    }

    /**
     * A simple pager adapter that represents ImageFragment objects, in
     * sequence.
     */
    private inner class ImagePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

        override fun createFragment(position: Int): Fragment {
            return ImageFragment().newInstance(imageList?.get(position))
        }

        override fun getItemCount(): Int {
            return imageList?.size ?: 0
        }
    }
}

