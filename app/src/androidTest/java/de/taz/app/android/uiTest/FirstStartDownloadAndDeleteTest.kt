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

    private val log by Log

    @get:Rule
    var activityScenarioRule = FreshAppStartRule(SplashActivity::class.java)

    @Test
    fun firstStartDownloadAndDeleteTest() {

        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()

        val closeButton = onView(
            allOf(
                withId(R.id.button_close), withText("×"),
                isDisplayed()
            )
        )
        MatchAssertionPoller(closeButton).waitFor(5000)

        log.info("Clicked x at onboarding")

        closeButton.perform(click())

        log.info("Waiting for Coverflow")

        longSettle()
        longSettle()
        longSettle()

        log.info("Dismiss try pdf dialog")
        dismissTryPdfDialog()

        longSettle()
        longSettle()


        log.info("Clicking on download")
        val constraintLayout = onView(
            allOf(
                withId(R.id.fragment_coverflow_moment_download),
                isDisplayed()
            )
        )
        constraintLayout.perform(click())

        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()
        longSettle()


        log.info("Opening bottom sheet")
        var momentCoverFlowItem = onMomentCoverFlowItemAt(0)
        momentCoverFlowItem.perform(longClick())

        longSettle()
        longSettle()

        log.info("Clicking delete")
        val materialTextView2 = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_delete),
                withText(R.string.fragment_bottom_sheet_issue_delete),
                childAtPosition(
                    childAtPosition(
                        withId(com.google.android.material.R.id.design_bottom_sheet),
                        0
                    ),
                    4
                ),
                isDisplayed()
            )
        )
        materialTextView2.perform(click())

        onView(allOf(withId(R.id.fragment_container))).check(doesNotExist())

        // Long click listener needs time to be responsive again, did not find a proper way to do it with idling resources
        longSettle()
        longSettle()

        onView(withId(R.id.fragment_cover_flow_grid)).perform(swipeRight())

        momentCoverFlowItem = onMomentCoverFlowItemAt(0)
        momentCoverFlowItem.perform(longClick())


        log.info("Waiting for botom sheet")
        val sheetDownloadItem = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_download),
                withText(R.string.fragment_bottom_sheet_issue_download),
                withParent(withParent(withId(com.google.android.material.R.id.design_bottom_sheet))),
                isDisplayed()
            )
        )
        MatchAssertionPoller(sheetDownloadItem).waitFor(2000)

        log.info("Clicking on download")
        val materialTextView3 = onView(
            allOf(
                withId(R.id.fragment_bottom_sheet_issue_download),
                withText(R.string.fragment_bottom_sheet_issue_download),
                childAtPosition(
                    childAtPosition(
                        withId(com.google.android.material.R.id.design_bottom_sheet),
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
