package de.taz.app.android.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.util.NightModeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class NightModeViewBindingActivity<ViewBindingType: ViewBinding>() : ViewBindingActivity<ViewBindingType>() {

    private val tazApiCssDataStore by lazy {
        TazApiCssDataStore.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenCreated {
            val darkModeEnabled = tazApiCssDataStore.nightMode.get()

            // Set initial theme by reading the preferences
            AppCompatDelegate.setDefaultNightMode(
                if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

    }

    override fun onResume() {
        super.onResume()
        tazApiCssDataStore.nightMode.asLiveData().observe(this) {
            if(it != NightModeHelper.isDarkTheme(this))
            CoroutineScope(Dispatchers.Main).launch {
                NightModeHelper.apply {
                    generateCssOverride(this@NightModeViewBindingActivity)
                    setThemeAndReCreate(this@NightModeViewBindingActivity, it)
                }
            }
        }
        tazApiCssDataStore.fontSize.asLiveData().observe(this) {
            CoroutineScope(Dispatchers.Main).launch {
                NightModeHelper.generateCssOverride(this@NightModeViewBindingActivity)
            }
        }
    }

}