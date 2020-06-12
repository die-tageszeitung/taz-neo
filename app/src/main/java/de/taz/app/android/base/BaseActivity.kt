package de.taz.app.android.base

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.NightModeHelper
import de.taz.app.android.singletons.SubscriptionPollHelper

abstract class BaseActivity(private val layoutId: Int? = null): AppCompatActivity() {

    private val log by Log

    private lateinit var tazApiCssPreferences: SharedPreferences

    private lateinit var tazApiCssPrefListener : SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        layoutId?.let { setContentView(layoutId) }

        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        NightModeHelper.initializeNightModePrefs(tazApiCssPreferences, this)

        tazApiCssPrefListener = NightModeHelper.PrefListener(this).tazApiCssPrefListener
    }

    override fun onResume() {
        super.onResume()
        createSingletons()
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    private fun createSingletons() {
        log.info("creating singletons")
        applicationContext.let {
            AppDatabase.createInstance(it)

            AppInfoRepository.createInstance(it)
            ArticleRepository.createInstance(it)
            DownloadRepository.createInstance(it)
            FileEntryRepository.createInstance(it)
            IssueRepository.createInstance(it)
            PageRepository.createInstance(it)
            ResourceInfoRepository.createInstance(it)
            SectionRepository.createInstance(it)

            AuthHelper.createInstance(it)
            DateHelper.createInstance(it)
            DownloadedIssueHelper.createInstance(it)
            FeedHelper.createInstance(it)
            FileHelper.createInstance(it)
            NotificationHelper.createInstance(it)
            OkHttp.createInstance(it)
            SubscriptionPollHelper.createInstance(it)
            ToastHelper.createInstance(it)
            ToDownloadIssueHelper.createInstance(it)

            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)

            DownloadService.createInstance(it)

            FirebaseHelper.createInstance(it)
        }
        log.debug("Singletons initialized")
    }

    override fun onPause() {
        super.onPause()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

}