package de.taz.app.android

import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.download.Download
import de.taz.app.android.download.DownloadService
import de.taz.app.android.util.AuthHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity(private val apiService: ApiService = ApiService()) : AppCompatActivity() {

    private val authHelper = AuthHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        helloWorld.loadUrl(
            "file:///storage/emulated/0/Android/data/de.taz.android.app.fdroid.debug/files/2019-08-24/section.21714.html"
        )

        login.setOnClickListener {
            GlobalScope.launch {
                apiService.authenticate(username.text.toString(), password.text.toString()).token?.let {
                    authHelper.token = it
                }
            }
        }


    }

}