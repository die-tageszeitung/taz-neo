package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import kotlinx.android.synthetic.main.fragment_article_read_on.*

class ArticleLoginFragment :
    BaseMainFragment<LoginContract.Presenter>(),
    LoginContract.View {

    override val presenter = LoginPresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_article_read_on, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_article_read_on_login_button.setOnClickListener {
            login()
        }

        fragment_article_read_on_password.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    login()
                    return true
                }
                return false
            }
        })
    }

    private fun login() {
        presenter.login(
            fragment_article_read_on_username.text.toString(),
            fragment_article_read_on_password.text.toString()
        )
        getMainView()?.hideKeyboard()
    }

    override fun showLoadingScreen() {
    }

    override fun hideLoadingScreen() {
    }

}