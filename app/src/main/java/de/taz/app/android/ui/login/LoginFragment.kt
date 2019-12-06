package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import kotlinx.android.synthetic.main.fragment_login.*

class LoginFragment :
    BaseMainFragment<LoginContract.Presenter>(),
    BackFragment, LoginContract.View {

    override val presenter = LoginPresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_login_login_button.setOnClickListener {
            login()
        }

        fragment_login_password.setOnEditorActionListener(object : TextView.OnEditorActionListener {
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
            fragment_login_username.text.toString(),
            fragment_login_password.text.toString()
        )
        getMainView()?.hideKeyBoard()
    }

    override fun showLoadingScreen() {
        fragment_login_loading_screen.visibility = View.VISIBLE
    }

    override fun hideLoadingScreen() {
        fragment_login_loading_screen.visibility = View.GONE
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

}