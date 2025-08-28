package de.taz.app.android.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityImagePagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.ui.webview.IMAGE_NAME
import de.taz.app.android.ui.webview.ImageFragment
import de.taz.app.android.util.Log
import kotlinx.coroutines.runBlocking


class ImagePagerActivity : ViewBindingActivity<ActivityImagePagerBinding>() {

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    private lateinit var viewPager2: ViewPager2
    private lateinit var tabLayout: TabLayout

    private lateinit var availableImageList: List<Image>
    private val uniqueImageKeys: List<String>
        get() = availableImageList
            .mapNotNull { it.name.split(".").getOrNull(1) }
            .distinct()

    private lateinit var displayableName: String
    private lateinit var pagerAdapter: ImagePagerAdapter
    private lateinit var imageName: String

    val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            displayableName = intent.extras!!.getString(DISPLAYABLE_NAME)!!
        } catch (e: NullPointerException) {
            val hint = "DISPLAYABLE_NAME not set, finishing ImagePagerActivity"
            log.error(hint)
            SentryWrapper.captureMessage(hint)
            finish()
        }

        imageName = intent.extras?.getString(IMAGE_NAME) ?: ""

        // Instantiate a ViewPager
        viewPager2 = findViewById(R.id.activity_image_pager)

        // Instantiate a TabLayout for page indicator
        tabLayout = findViewById(R.id.activity_image_pager_tab_layout)


        // TODO should not need to be blocking -> move to ViewModel
        runBlocking {
            availableImageList = if (displayableName.startsWith("section.")) {
                // for sections just load the clicked image
                SectionRepository.getInstance(applicationContext).imagesForSectionStub(displayableName).filter {
                    it.name.startsWith(imageName.removeSuffix("norm.jpg"))
                }
            } else {
                ArticleRepository.getInstance(applicationContext).getImagesForArticle(displayableName)
            }
        }

        // Instantiate pager adapter, which provides the pages to the view pager widget.
        pagerAdapter = ImagePagerAdapter(this)
        viewPager2.adapter = pagerAdapter
        imageName.split(".").getOrNull(1)?.let { key ->
            viewPager2.setCurrentItem(uniqueImageKeys.indexOf(key), false)
        }

        viewPager2.apply {
            reduceDragSensitivity(6)
            offscreenPageLimit = 2
        }

        viewBinding.buttonClose.setOnClickListener {
            finish()
        }
    }

    override fun onAttachedToWindow() {
        val itemCount = viewPager2.adapter?.itemCount ?: 0
        if (itemCount > 1) {
            TabLayoutMediator(tabLayout, viewPager2) { _, _ ->
            }.attach()
        }
        super.onAttachedToWindow()
    }

    private fun imageByKeyAndResolution(key: String, resolution: ImageResolution): Image? {
        return availableImageList.findLast {
            it.name.split(".").getOrNull(1) == key && it.resolution == resolution
        }
    }

    /**
     * Get a pair of images, first being an image to display immediately as it should already be downloaded.
     * Second an image image in a higher resolution that can be downloaded after displaying the item to enhance quality
     * Both might be null as their might either be no already downloaded version or no high resoltion version of this image.
     * If both are null the requested key was not found at all.
     * @param key key of the requested image
     * @return pair of one low and one high resolution version of the image
     */
    private fun getImageSet(key: String): Pair<Image?, Image?> {
        val lowResolution =
            imageByKeyAndResolution(key, ImageResolution.normal) ?: imageByKeyAndResolution(
                key,
                ImageResolution.small
            )
        val highResolution = imageByKeyAndResolution(key, ImageResolution.high)
        return lowResolution to highResolution
    }

    /**
     * A simple pager adapter that represents ImageFragment objects, in
     * sequence.
     */
    private inner class ImagePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            val imageKey = uniqueImageKeys[position]
            val imageSet = getImageSet(imageKey)
            return ImageFragment.newInstance(
                imageSet.first,
                imageSet.second
            )
        }

        override fun getItemCount(): Int {
            return uniqueImageKeys.size
        }
    }
}

