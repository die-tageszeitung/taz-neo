package de.taz.app.android.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.base.BaseContract
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.ui.main.MainContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SettingsInnerFragment : PreferenceFragmentCompat(), BaseContract.View  {
    val issueRepository = IssueRepository.getInstance()

    override fun getMainView(): MainContract.View? {
        return activity as? MainContract.View
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)

        val manageAccountPreference : Preference? = findPreference("manage_account")
        manageAccountPreference?.setOnPreferenceClickListener {
            this.getMainView()?.showMainFragment(LoginFragment())
            true
        }

        findPreference<CustomSwitchPreference>("text_night_mode")?.preferenceManager?.sharedPreferencesName = PREFERENCES_TAZAPICSS
        findPreference<TextSizePreference>("text_font_size")?.preferenceManager?.sharedPreferencesName = PREFERENCES_TAZAPICSS

        val keepNumberIssues = findPreference<EditTextPreference>("general_keep_number_issues")
        keepNumberIssues?.preferenceManager?.sharedPreferencesName = PREFERENCES_GENERAL

        keepNumberIssues?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        keepNumberIssues?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
           "$text ${resources.getString(R.string.settings_general_keep_number_issues_days)}"
        }

        keepNumberIssues?.setOnPreferenceChangeListener { preference, newValue ->
            (preference as? EditTextPreference)?.let { editTextPreference ->
                (newValue as? String)?.let {
                    if (newValue.toInt() < editTextPreference.text.toInt()) { //check whether new storage limit is smaller, only then check whether number of saved issues is exceeded
                        lifecycleScope.launch(Dispatchers.IO) {
                            issueRepository.getAllDownloadedStubs()?.let { allDownloadedStubs ->
                                if (allDownloadedStubs.size > newValue.toInt()) {
                                    //delete older issues
                                    var numberDownloadedIssues = allDownloadedStubs.size
                                    while (numberDownloadedIssues > newValue.toInt()) {
                                        issueRepository.getEarliestDownloadedIssue()?.let {
                                            issueRepository.delete(it)
                                            numberDownloadedIssues--
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        }

    }
}