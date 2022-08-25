package de.taz.app.android.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import de.taz.app.android.ui.main.MAIN_EXTRA_ARTICLE

/**
 * Contract for starting [LoginActivity] with the [Input] parameters for logging in or
 * registering a user
 */
class LoginContract : ActivityResultContract<LoginContract.Input, LoginContract.Output>() {

    enum class Option {
        LOGIN,
        REGISTER,
        PRINT_TO_DIGI,
        EXTEND_PRINT
    }


    /**
     * @param register whether to register a user - false by default so trying to log in
     * @param articleFileName - if coming from an article provide its name - this has influences
     *          on the UI in the LoginActivity
     */
    data class Input(
        val option: Option = Option.LOGIN,
        val username: String? = null,
        val password: String? = null,
        val articleFileName: String? = null
    )

    data class Output(
        val success: Boolean,
        val articleFileName: String?
    )

    override fun createIntent(context: Context, input: Input) =
        Intent(context, LoginActivity::class.java).apply {
            putExtra(LOGIN_EXTRA_USERNAME, input.username)
            putExtra(LOGIN_EXTRA_PASSWORD, input.password)
            putExtra(LOGIN_EXTRA_OPTION, input.option.toString())
            putExtra(LOGIN_EXTRA_ARTICLE, input.articleFileName)
        }

    override fun parseResult(resultCode: Int, intent: Intent?) = Output(
        success = resultCode == Activity.RESULT_OK,
        articleFileName = intent?.getStringExtra(MAIN_EXTRA_ARTICLE),
    )

}