package de.taz.app.android.ui.listen

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentListenContainerBinding

class ListenContainerFragment : ViewBindingFragment<FragmentListenContainerBinding>() {

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
    }

    private var selectedPosition = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        selectedPosition = savedInstanceState?.getInt(KEY_SELECTED_TAB, 0) ?: 0

        viewBinding?.let { binding ->
            binding.tabPodcasts.setOnClickListener {
                showFragment(0)
            }
            binding.tabPlaylist.setOnClickListener {
                showFragment(1)
            }
            updateTabSelection(selectedPosition)
        }

        if (savedInstanceState == null) {
            showFragment(0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedPosition)
    }

    private fun showFragment(position: Int) {
        if (selectedPosition == position && childFragmentManager.findFragmentById(R.id.listen_fragment_container) != null) {
            updateTabSelection(position)
            return
        }

        selectedPosition = position
        updateTabSelection(position)

        val fragment = when (position) {
            0 -> ListenPodcastFragment()
            1 -> ListenPlaylistFragment()
            else -> return
        }

        childFragmentManager.commit {
            replace(R.id.listen_fragment_container, fragment)
        }
    }

    private fun updateTabSelection(position: Int) {
        viewBinding?.apply {
            tabPodcasts.isSelected = position == 0
            tabPlaylist.isSelected = position == 1

            // Update content descriptions for accessibility to announce selection state
            tabPodcasts.contentDescription = getString(R.string.tab_bar_listen_podcasts) +
                    if (position == 0) ", " + getString(R.string.listen_podcasts_selected_accessibility) else ""
            tabPlaylist.contentDescription = getString(R.string.tab_bar_listen_playlist) +
                    if (position == 1) ", " + getString(R.string.listen_podcasts_selected_accessibility) else ""

            // Set the icon tint manually as the layout.isSelected does not tint the icons
            if (position == 0) {
                tabPodcastsIcon.setColorFilter(
                    resources.getColor(R.color.textColor, null)
                )
                tabPlaylistIcon.setColorFilter(
                    resources.getColor(R.color.textColorAccent, null)
                )
            } else {
                tabPlaylistIcon.setColorFilter(
                    resources.getColor(R.color.textColor, null)
                )
                tabPodcastsIcon.setColorFilter(
                    resources.getColor(R.color.textColorAccent, null)
                )
            }
        }
    }
}
