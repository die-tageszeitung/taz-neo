package de.taz.app.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.drawer.SectionListFragment
import de.taz.app.android.ui.drawer.SelectedIssueViewModel
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val log by Log

    private val apiService: ApiService = ApiService()
    private lateinit var authHelper: AuthHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper

    private lateinit var viewModel: SelectedIssueViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authHelper = AuthHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewModel = ViewModelProviders.of(this@MainActivity).get(SelectedIssueViewModel::class.java)
        supportFragmentManager.beginTransaction().replace(R.id.drawerMenuFragmentPlaceHolder, SectionListFragment()).commit()


        CoroutineScope(Dispatchers.IO).launch {
            try {
                val issue = apiService.getIssueByFeedAndDate()
                issueRepository.save(issue)
                DownloadService.download(applicationContext, issue)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
            } catch (e: ApiService.ApiServiceException.InsufficientData) {
                toastHelper.makeToast(R.string.toast_unknown_error)
            }
        }

        issueRepository.getLatestIssueBaseLiveData()
            .observe(this@MainActivity, Observer { issueBase ->
                log.debug("issue exists in db")
                issueBase?.isDownloadedLiveData()
                    ?.observe(this@MainActivity, Observer { downloaded ->
                        if (downloaded) {
                            log.debug("issue is downloaded")
                            CoroutineScope(Dispatchers.IO).launch {
                                val issue = issueBase.getIssue()
                                viewModel.selectedIssue.postValue(issue)
                            }
                        }
                    })
            })

    }

}
