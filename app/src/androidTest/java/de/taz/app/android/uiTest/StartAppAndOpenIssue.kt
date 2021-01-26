package de.taz.app.android.uiTest

import androidx.annotation.RequiresApi
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import de.taz.app.android.R
import de.taz.app.android.rules.FreshAppStartRule
import de.taz.app.android.suite.UiTestSuite
import de.taz.app.android.ui.splash.SplashActivity
import de.taz.app.android.uiSynchronization.InitializationResource
import de.taz.app.android.util.*
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test


@LargeTest
@UiTestSuite
/**
 * Before API 24 the webview never becomes idle leading to test to become stuck, no fix is found so far
 */
@RequiresApi(24)
class StartAppAndOpenIssue {
    @get:Rule
    var activityScenarioRule = FreshAppStartRule(SplashActivity::class.java)


    @Before
    fun setup() {
        IdlingRegistry.getInstance().register(InitializationResource)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(InitializationResource)
    }

    @Test
    fun startAppAndOpenIssue() {
        onView(withId(R.id.data_policy_page_scroll_view))
            .perform(swipeUp())
        val acceptButton = onView(withId(R.id.data_policy_accept_button))
        MatchAssertionPoller(acceptButton).waitFor(2000)

        shortSettle()

        PerformPoller(acceptButton, click()).waitFor(2000)

        val closeButton = onView(
            allOf(
                withId(R.id.button_close), withText("×"),
                isDisplayed()
            )
        )
        MatchAssertionPoller(closeButton).waitFor(2000)


        closeButton.perform(click())

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
