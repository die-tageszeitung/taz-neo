package de.taz.app.android.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.ui.webview.IMAGE_NAME
import de.taz.app.android.ui.webview.ImageFragment
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max


class ImagePagerActivity : NightModeActivity(R.layout.activity_image_pager) {

    private lateinit var viewPager2: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var displayableName: String? = null
    private var imageName: String? = null
    private var availableImageList: MutableList<Image>? = null
    private var toDownloadImageList: List<Image> = emptyList()
    private var pagerAdapter: ImagePagerAdapter? = null

    private lateinit var dataService: DataService

    val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataService = DataService.getInstance()

        displayableName = intent.extras?.getString(DISPLAYABLE_NAME)
        imageName = intent.extras?.getString(IMAGE_NAME)

        // Instantiate a ViewPager
        viewPager2 = findViewById(R.id.activity_image_pager)

        // Instantiate a TabLayout for page indicator
        tabLayout = findViewById(R.id.activity_image_pager_tab_layout)

        // Instantiate pager adapter, which provides the pages to the view pager widget.
        pagerAdapter = pagerAdapter ?: ImagePagerAdapter(this)
        viewPager2.adapter = pagerAdapter

        lifecycleScope.launch(Dispatchers.IO) {
            setImages()
            runOnUiThread {
                viewPager2.setCurrentItem(getPosition(imageName), false)
            }
            withContext(Dispatchers.IO) {
                toDownloadImageList.forEach { img ->
                    dataService.ensureDownloaded(FileEntry(img), img.getIssueStub().baseUrl)
                }
            }
        }

        viewPager2.apply {
            reduceDragSensitivity(6)
            offscreenPageLimit = 2
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

    private suspend fun setImages() = withContext(Dispatchers.IO) {
        displayableName?.let { displayableName ->
            val allImages = if (displayableName.startsWith("section.")) {
                SectionRepository.getInstance().imagesForSectionStub(displayableName)
            } else {
                ArticleRepository.getInstance().getImagesForArticle(displayableName)
            }

            val downloadedImages =
                allImages.filter { it.dateDownload != null }.toMutableList()
            val imagesToDownload = allImages.toMutableList()
            imagesToDownload.removeAll(downloadedImages)

            downloadedImages.removeAll { image ->
                val name = image.name
                name.contains("norm.")
                        && downloadedImages.firstOrNull {
                    it.name == name.replace("norm.", "high.")
                } != null
            }
            availableImageList = downloadedImages
            toDownloadImageList = imagesToDownload
        }
    }

    private fun getPosition(imageName: String?): Int {
        val highResPosition =
            availableImageList?.indexOfFirst { it.name == imageName?.replace("norm", "high") }
                ?.coerceAtLeast(0) ?: 0
        val normalResPosition =
            availableImageList?.indexOfFirst { it.name == imageName }?.coerceAtLeast(0) ?: 0
        return max(highResPosition, normalResPosition)
    }

    /**
     * A simple pager adapter that represents ImageFragment objects, in
     * sequence.
     */
    private inner class ImagePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

        override fun createFragment(position: Int): Fragment {
            val image = availableImageList?.get(position)
            var toBeDownloadedImage: Image? = null
            if (image?.resolution != ImageResolution.high && toDownloadImageList.isNotEmpty()) {
                toBeDownloadedImage = toDownloadImageList.firstOrNull { highRes ->
                    image?.name?.replace("norm", "high") == highRes.name
                }
            }
            return ImageFragment().newInstance(
                image,
                toBeDownloadedImage
            )
        }

        override fun getItemCount(): Int {
            return availableImageList?.size ?: 0
        }
    }
}

