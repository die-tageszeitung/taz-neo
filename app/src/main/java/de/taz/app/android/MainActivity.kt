package de.taz.app.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.util.AuthHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity(private val apiService: ApiService = ApiService()) : AppCompatActivity() {

    private val authHelper = AuthHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        test.setOnClickListener {
            GlobalScope.launch {
                val string = apiService.getIssueByFeedAndDate().toString()
                runOnUiThread {
                    helloWorld.text = string
                }
            }
        }

        login.setOnClickListener {
            GlobalScope.launch {
                apiService.authenticate(username.text.toString(), password.text.toString()).token?.let {
                    authHelper.token = it
                }
                runOnUiThread {
                    helloWorld.text = authHelper.token
                }
            }
        }


    }

}