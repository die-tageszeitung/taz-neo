package de.taz.app.android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        createSingletons()
        GlobalScope.launch {
            AppInfoRepository().save(ApiService().getAppInfo())
            ToastHelper.getInstance(applicationContext).makeToast(AppInfoRepository().get().globalBaseUrl)
            ResourceInfoRepository().save(ApiService().getResourceInfo())
            ToastHelper.getInstance().makeToast(ResourceInfoRepository().getOrThrow().resourceList.first().name)
            IssueRepository().save(ApiService().getIssueByFeedAndDate())
            ToastHelper.getInstance().makeToast(IssueRepository().getLatestIssueBase().feedName)

            //IssueRepository.save(ApiService().getIssueByFeedAndDate())
            ToastHelper.getInstance().makeToast(IssueRepository().getLatestIssue().sectionList.first().articleList.first().title.toString())
        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        AppDatabase.createInstance(applicationContext)
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
    }

}

