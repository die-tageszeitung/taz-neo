package de.taz.app.android.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.data.DataService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.singletons.*
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.SubscriptionPollHelper

abstract class BaseActivity(private val layoutId: Int? = null): AppCompatActivity() {

    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSingletons()
        layoutId?.let { setContentView(layoutId) }
    }

    private lateinit var downloadService: DownloadService

    private fun createSingletons() {
        log.info("creating singletons")
        applicationContext.let {
            AppDatabase.createInstance(it)

            AuthHelper.createInstance(it)
            DownloadedIssueHelper.createInstance(it)
            FeedHelper.createInstance(it)
            FileHelper.createInstance(it)
            NotificationHelper.createInstance(it)
            SubscriptionPollHelper.createInstance(it)
            ToastHelper.createInstance(it)

            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)

            downloadService = DownloadService.createInstance(it)

            FirebaseHelper.createInstance(it)
            DataService.createInstance(it)
        }
        log.debug("Singletons initialized")
    }
}