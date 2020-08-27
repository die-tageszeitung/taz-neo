package de.taz.app.android.ui.login.fragments.subscription

/*
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.login.LoginActivity
import io.sentry.Sentry
import kotlinx.android.synthetic.main.fragment_subscription_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.Throws
class SubscriptionPagerFragment : BaseViewModelFragment<SubscriptionViewModel>(
    R.layout.fragment_subscription_pager
), BackFragment {

    private var toastHelper: ToastHelper? = null
    private var isSubscriptionRequestRunning = AtomicBoolean(false)

    val fragments = listOf(
        SubscriptionPriceFragment(),
        SubscriptionAddressFragment(),
        SubscriptionBankFragment(),
        SubscriptionAccountFragment()
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        subscription_view_pager.apply {
            adapter = SubscriptionPagerAdapter(
                childFragmentManager,
                lifecycle
            )
            reduceDragSensitivity(2)
        }
        toastHelper = ToastHelper.getInstance(context?.applicationContext)
    }

    fun next() {
        subscription_view_pager.currentItem = subscription_view_pager.currentItem + 1
    }

    private inner class SubscriptionPagerAdapter(
        fragmentManager: FragmentManager, lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {


        override fun getItemCount(): Int {
            return fragments.size
        }

        @Throws(java.lang.IllegalStateException::class)
        override fun createFragment(position: Int): Fragment {
            return fragments.getOrNull(position)
                ?: throw IllegalStateException("Invalid position in ViewPager")
        }
    }

    override fun onBackPressed(): Boolean {
        return if (subscription_view_pager.currentItem > 0) {
            subscription_view_pager.currentItem = subscription_view_pager.currentItem - 1
            true
        } else {
            false
        }
    }

    fun allDone() {
        fragments.forEachIndexed { index, subscriptionBaseFragment ->
            if (!subscriptionBaseFragment.done()) {
                subscription_view_pager.currentItem = index
                return
            }
        }
        sendSubscriptionRequest()
    }

    private fun sendSubscriptionRequest() {
/*        try {
            if (!isSubscriptionRequestRunning.getAndSet(true)) {
                showLoadingScreen()
                lifecycleScope.launch(Dispatchers.IO) {
                    ApiService.getInstance(context?.applicationContext).subscription(
                        viewModel.email,
                        viewModel.password,
                        viewModel.surName,
                        viewModel.firstName,
                        viewModel.street,
                        viewModel.city,
                        viewModel.postCode,
                        viewModel.country,
                        viewModel.phone,
                        viewModel.price,
                        viewModel.iban,
                        viewModel.accountHolder
                    )?.let { subscriptionInfo ->
                        when (subscriptionInfo.status) {
                            SubscriptionStatus.ibanNoIban -> {
                                getFragment<SubscriptionBankFragment>()?.setIbanError(R.string.iban_error_empty)
                            }
                            SubscriptionStatus.ibanInvalidChecksum -> {
                                getAndShowFragment<SubscriptionBankFragment>()?.setIbanError(R.string.iban_error_invalid)
                            }
                            SubscriptionStatus.ibanNoSepaCountry -> {
                                getAndShowFragment<SubscriptionBankFragment>()?.setIbanError(R.string.iban_error_no_sepa)
                            }
                            SubscriptionStatus.invalidAccountHolder -> {
                                getAndShowFragment<SubscriptionBankFragment>()?.setAccountHolderError(
                                    R.string.account_holder_invalid
                                )
                            }
                            SubscriptionStatus.invalidMail -> {
                                getAndShowFragment<SubscriptionAccountFragment>()?.setEmailError(R.string.login_email_error_empty)
                            }
                            SubscriptionStatus.invalidFirstName -> {
                                getAndShowFragment<SubscriptionAddressFragment>()?.setFirstNameError(
                                    R.string.first_name_error_invalid
                                )
                            }
                            SubscriptionStatus.invalidSurname -> {
                                getAndShowFragment<SubscriptionAddressFragment>()?.setSurnameError(R.string.login_surname_error_invalid)
                            }
                            SubscriptionStatus.noFirstName -> {
                                getAndShowFragment<SubscriptionAddressFragment>()?.setFirstNameError(
                                    R.string.first_name_error_empty
                                )
                            }
                            SubscriptionStatus.noSurname -> {
                                getAndShowFragment<SubscriptionAddressFragment>()?.setSurnameError(R.string.surname_error_empty)
                            }
                            SubscriptionStatus.priceNotValid -> {
                                getAndShowFragment<SubscriptionPriceFragment>()?.showPriceError()
                            }
                            SubscriptionStatus.waitForMail -> {
                                AuthHelper.getInstance(context?.applicationContext).isPolling = true
                                parentFragmentManager.beginTransaction().replace(
                                    R.id.activity_subscription_fragment_placeholder,
                                    SubscriptionConfirmMailFragment()
                                ).commit()
                            }
                            SubscriptionStatus.waitForProc -> {
                                AuthHelper.getInstance(context?.applicationContext).isPolling = true
                            }
                            SubscriptionStatus.alreadyLinked -> {
                                activity?.startActivity(
                                    Intent(
                                        activity?.applicationContext,
                                        LoginActivity::class.java
                                    )
                                )
                            }
                            SubscriptionStatus.tazIdNotValid -> {
                                // user doesn't have a tazID yet
                                // TODO SEND TO LOGINACTIVITY TO CREATE ONE?
                                toastHelper?.showSomethingWentWrongToast()
                                hideLoadingScreen()
                            }
                            SubscriptionStatus.valid,
                            SubscriptionStatus.invalidConnection,
                            SubscriptionStatus.noPollEntry,
                            SubscriptionStatus.toManyPollTrys,
                            SubscriptionStatus.subscriptionIdNotValid,
                            SubscriptionStatus.elapsed -> {
                                // this should not happen
                                Sentry.capture("subscription returned ${subscriptionInfo.status} ")
                                toastHelper?.showSomethingWentWrongToast()
                                hideLoadingScreen()

                            }
                        }
                        isSubscriptionRequestRunning.set(false)
                    } ?: run {
                        isSubscriptionRequestRunning.set(false)
                        toastHelper?.showSomethingWentWrongToast()
                        Sentry.capture("subscription returned null")
                        hideLoadingScreen()
                    }
                }
            }
        } catch (nie: ApiService.ApiServiceException.NoInternetException) {
            showRetrySnackBar()
        }*/
    }

    private fun showRetrySnackBar() {
        view?.let {
            Snackbar
                .make(it, R.string.toast_no_internet, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) {
                    sendSubscriptionRequest()
                }
                .show()
        }
    }

    private inline fun <reified T : SubscriptionBaseFragment> getAndShowFragment(): T? {
        return getFragment<T>()?.also { showFragment(it) }
    }

    private fun showFragment(fragment: Fragment) {
        activity?.runOnUiThread {
            subscription_view_pager?.currentItem = fragments.indexOf(fragment)
        }
    }

    private inline fun <reified T : SubscriptionBaseFragment> getFragment(): T? {
        return fragments.firstOrNull { it::class.java == T::class.java } as? T
    }

    private fun hideLoadingScreen() {
        activity?.runOnUiThread {
            activity?.findViewById<View>(R.id.loading_screen)?.visibility = View.GONE
        }
    }

    private fun showLoadingScreen() {
        activity?.runOnUiThread {
            activity?.findViewById<View>(R.id.loading_screen)?.visibility = View.VISIBLE
        }
    }
}
*/