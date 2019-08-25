package de.taz.app.android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.api.QueryService
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        createSingletons()
        GlobalScope.launch {
            ApiService().getAppInfo().save(applicationContext)
            ToastHelper.getInstance(applicationContext).makeToast(AppInfo.get(applicationContext).globalBaseUrl)
            ApiService().getResourceInfo().save(applicationContext)
            ToastHelper.getInstance().makeToast(ResourceInfo.get(applicationContext).resourceVersion.toString())
        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
    }

}

