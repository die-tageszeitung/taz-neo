package de.taz.app.android.ui.webview

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.singletons.FileHelper

class ImageFragment : Fragment() {
    var image: Image? = null

    fun newInstance(image: Image?): ImageFragment {
        val fragment = ImageFragment()
        fragment.image = image
        return fragment
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_image, container, false)
        val imageView = view.findViewById<ImageView>(R.id.image_view)
        image?.let {
            val fileHelper = FileHelper.getInstance(context)
            context?.let { it1 ->
                fileHelper.getFileDirectoryUrl(it1).let { fileDir ->

                    val uri = Uri.parse("${fileDir}/${it.folder}/${it.name}")
                    imageView.setImageURI(uri)

                }
            }
        }
        return view
    }

}