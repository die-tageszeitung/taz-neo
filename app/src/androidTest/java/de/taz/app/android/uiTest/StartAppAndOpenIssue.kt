package de.taz.app.android.uiTest

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import de.taz.app.android.R
import de.taz.app.android.suite.UiTestSuite
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.*
import org.junit.Rule
import org.junit.Test


@LargeTest
@UiTestSuite
/**
 * Before API 24 the webview never becomes idle leading to test to become stuck, no fix is found so far
 */
@SdkSuppress(minSdkVersion = 24)
class StartAppAndOpenIssue {
    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)


    @Test
    fun startAppAndOpenIssue() {

        longSettle()
        longSettle()

        onMomentCoverFlowItemAt(0)
            .perform(click())

        val sectionDrawerMoment = onView(withId(R.id.fragment_drawer_sections_moment))
        MatchAssertionPoller(sectionDrawerMoment).waitFor(2000)

        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()

        val titleSection = onView(childAtPosition(withId(R.id.fragment_drawer_sections_list), 0))
        titleSection.check(matches(withText("titel")))
    }
}
