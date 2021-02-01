package de.taz.app.android.uiTest

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import de.taz.app.android.R
import de.taz.app.android.rules.FreshAppStartRule
import de.taz.app.android.suite.UiTestSuite
import de.taz.app.android.ui.splash.SplashActivity
import de.taz.app.android.util.*
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test


@LargeTest
@UiTestSuite
class FirstStartDownloadAndDeleteTest {
    @get:Rule
    var activityScenarioRule = FreshAppStartRule(SplashActivity::class.java)

    @Test
    fun firstStartDownloadAndDeleteTest() {

        longSettle()
        longSettle()

        val dataPolicyContent = onView(withId(R.id.data_policy_fullscreen_content))
        MatchAssertionPoller(dataPolicyContent, isDisplayed()).waitFor(10000)

        onView(withId(R.id.data_policy_page_scroll_view))
            .perform(swipeUp())
        val acceptButton = onView(withId(R.id.data_policy_accept_button))

        MatchAssertionPoller(acceptButton).waitFor(2000)

        shortSettle()
        acceptButton.perform(click())

        longSettle()

        val closeButton = onView(
            allOf(
                withId(R.id.button_close), withText("×"),
                isDisplayed()
            )
        )
        MatchAssertionPoller(closeButton).waitFor(5000)


        closeButton.perform(click())

        longSettle()
        longSettle()
        longSettle()
        longSettle()

        val constraintLayout = onView(
            allOf(
                withId(R.id.view_moment_download_icon_wrapper),
                childAtPosition(
                    allOf(
                        withId(R.id.fragment_archive_item),
                        childAtPosition(
                            withId(R.id.fragment_cover_flow_item),
                            0
                        )
                    ),
                    2
                ),
                isDisplayed()
            )
        )
        constraintLayout.perform(click())

        longSettle()
        longSettle()

        var momentCoverFlowItem = onMomentCoverFlowItemAt(0)
        momentCoverFlowItem.perform(longClick())


        val textView = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_delete), withText(R.string.fragment_bottom_sheet_issue_delete),
                withParent(withParent(withId(R.id.design_bottom_sheet))),
                isDisplayed()
            )
        )

        longSettle()

        val materialTextView2 = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_delete), withText(R.string.fragment_bottom_sheet_issue_delete),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.design_bottom_sheet),
                        0
                    ),
                    4
                ),
                isDisplayed()
            )
        )
        materialTextView2.perform(click())

        onView(allOf(withId(R.id.dialog_bottom_sheet))).check(doesNotExist())

        // Long click listener needs time to be responsive again, did not find a proper way to do it with idling resources
        longSettle()

        onView(withId(R.id.fragment_cover_flow_grid)).perform(swipeRight())

        momentCoverFlowItem = onMomentCoverFlowItemAt(0)
        momentCoverFlowItem.perform(longClick())

        val sheetDownloadItem = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_download),
                withText(R.string.fragment_bottom_sheet_issue_download),
                withParent(withParent(withId(R.id.design_bottom_sheet))),
                isDisplayed()
            )
        )
        MatchAssertionPoller(sheetDownloadItem).waitFor(2000)

        val materialTextView3 = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_download),
                withText(R.string.fragment_bottom_sheet_issue_download),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.design_bottom_sheet),
                        0
                    ),
                    5
                ),
                isDisplayed()
            )
        )
        materialTextView3.perform(click())
    }
}
