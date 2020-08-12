package de.taz.app.android.ui.login.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import de.taz.app.android.R
import de.taz.app.android.SUBSCRIPTION_EMAIL_ADDRESS
import de.taz.app.android.singletons.ToastHelper

class SubscriptionElapsedDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.subscription_elapsed_popup, container)

        val emailText = view.findViewById<TextView>(R.id.subscription_elapsed_popup_email)
        emailText.setOnClickListener {
            val email = Intent(Intent.ACTION_SEND)
            email.putExtra(Intent.EXTRA_EMAIL, arrayOf(SUBSCRIPTION_EMAIL_ADDRESS))
            email.putExtra(Intent.EXTRA_SUBJECT, "")
            email.putExtra(Intent.EXTRA_TEXT, "")
            email.type = "message/rfc822"
            startActivity(Intent.createChooser(email, null))
        }
        val cancelButton = view.findViewById<Button>(R.id.subscription_elapsed_popup_cancel_button)
        cancelButton.setOnClickListener {
            dismiss()
        }
        val orderButton = view.findViewById<Button>(R.id.subscription_elapsed_popup_order_button)
        orderButton.setOnClickListener {
            // TODO -> Go to order fragment which will be implemented soon
            ToastHelper.getInstance().showToast("Bald können Sie aus der App heraus bestellen!")
            dismiss()
        }
        return view
    }
}