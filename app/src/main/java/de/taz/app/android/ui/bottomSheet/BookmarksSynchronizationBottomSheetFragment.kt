package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentBookmarksSynchronisationBottomSheetBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.BookmarkRepository
import kotlinx.coroutines.launch

class BookmarksSynchronizationBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentBookmarksSynchronisationBottomSheetBinding>() {

    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var generalDataStore: GeneralDataStore

    companion object {
        const val TAG = "BookmarksSynchronizationBottomSheet"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.positiveButton.setOnClickListener {
            lifecycleScope.launch {
                val doNotAskAgain = viewBinding.doNotAskAgain.isChecked
                generalDataStore.bookmarksSynchronizationBottomSheetDoNotShowAgain.set(doNotAskAgain)
                generalDataStore.bookmarksSynchronizationEnabled.set(true)
                generalDataStore.bookmarksSynchronizationChangedToEnabled.set(true)
                showLoadingScreen()
                bookmarkRepository.checkForSynchronizedBookmarks()
                dismiss()
            }
        }
        viewBinding.negativeButton.setOnClickListener {
            lifecycleScope.launch {
                val doNotAskAgain = viewBinding.doNotAskAgain.isChecked
                generalDataStore.bookmarksSynchronizationBottomSheetDoNotShowAgain.set(doNotAskAgain)
                generalDataStore.bookmarksSynchronizationEnabled.set(false)
                dismiss()
            }
        }
        viewBinding.buttonClose.setOnClickListener { dismiss() }
    }

    private fun showLoadingScreen() {
        viewBinding.loadingScreen.visibility = View.VISIBLE
        viewBinding.content.visibility = View.GONE
    }
}