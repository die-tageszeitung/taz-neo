package de.taz.app.android.ui.login.fragments.subscription

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.radiobutton.MaterialRadioButton
import de.taz.app.android.R
import de.taz.app.android.api.models.PriceInfo
import de.taz.app.android.databinding.FragmentSubscriptionPriceBinding
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.coroutines.launch


class SubscriptionPriceFragment : SubscriptionBaseFragment<FragmentSubscriptionPriceBinding>() {
    private val priceInfoAdapter = PriceInfoAdapter()
    private var priceList = emptyList<PriceInfo>()

    private var invalidPrice: Boolean = false

    companion object {
        fun createInstance(
            priceList: List<PriceInfo>,
            invalidPrice: Boolean = false
        ): SubscriptionPriceFragment {
            val fragment = SubscriptionPriceFragment()
            fragment.priceList = priceList
            fragment.invalidPrice = invalidPrice
            return fragment
        }
    }

    private val testSubscriptionPriceInfo by lazy {
        PriceInfo(resources.getString(R.string.trial_subscription_name), 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // get async so when user reconnects it automatically resolves
        setPriceList(priceList)
        if (invalidPrice) {
            showPriceError()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentSubscriptionPriceList.apply {
            adapter = priceInfoAdapter
            layoutManager = LinearLayoutManager(context)
        }

        viewBinding.fragmentSubscriptionAddressProceed.setOnClickListener { ifDoneNext() }
    }

    override fun done(): Boolean {
        var done = true
        if (priceInfoAdapter.selectedItem == RecyclerView.NO_POSITION) {
            done = false
            showPriceError()
        }
        viewModel.price = priceInfoAdapter.getSelectedPriceInfo().price
        return done
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS)
    }

    private inner class PriceInfoAdapter : RecyclerView.Adapter<PriceInfoAdapter.ViewHolder>() {
        var selectedItem: Int = RecyclerView.NO_POSITION

        var priceInfoList = emptyList<PriceInfo>()


        fun setData(newPriceInfoList: List<PriceInfo>) {
            priceInfoList = newPriceInfoList
            notifyDataSetChanged()
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
            priceInfoList[i].let { priceInfo ->
                viewHolder.name.text = priceInfo.name
                viewHolder.price.text = "%.2f €".format(priceInfo.price / 100f)
                viewHolder.radio.isChecked = i == selectedItem
            }
        }

        override fun getItemCount(): Int {
            return priceInfoList.size
        }

        fun getSelectedPriceInfo(): PriceInfo {
            return priceInfoList[selectedItem]
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
            val inflater = LayoutInflater.from(context)
            val view: View =
                inflater.inflate(R.layout.fragment_subscription_price_list_item, viewGroup, false)
            return ViewHolder(view)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var radio: MaterialRadioButton =
                view.findViewById<View>(R.id.radio) as MaterialRadioButton
            var name: TextView = view.findViewById<View>(R.id.name) as TextView
            var price: TextView = view.findViewById<View>(R.id.price) as TextView

            init {
                itemView.setOnClickListener { radio.performClick() }
                radio.setOnClickListener {
                    selectedItem = bindingAdapterPosition
                    notifyItemRangeChanged(0, bindingAdapterPosition)
                    notifyItemRangeChanged(bindingAdapterPosition + 1, itemCount - (bindingAdapterPosition + 1))
                }
            }
        }
    }

    private fun showPriceError() {
        ToastHelper.getInstance(requireContext().applicationContext).showToast(R.string.price_error_none)
    }

    private fun setPriceList(it: List<PriceInfo>) {
        lifecycleScope.launch {
            val priceList = if (viewModel.isElapsed()) {
                mutableListOf()
            } else {
                mutableListOf(testSubscriptionPriceInfo)
            }
            priceList.addAll(it)
            if (priceInfoAdapter.itemCount == 0) {
                val position = viewModel.price?.let { selectedPrice ->
                    priceList.indexOfFirst { it.price == selectedPrice }
                }?.coerceAtLeast(0) ?: 0
                priceInfoAdapter.setData(priceList)
                priceInfoAdapter.selectedItem = position
                activity?.findViewById<View>(R.id.loading_screen)?.visibility =
                    View.GONE
            }
        }
    }
}