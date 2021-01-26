package de.taz.app.android.util

import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.ViewMatchers.*
import de.taz.app.android.R
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher

fun onMomentCoverFlowItemAt(position: Int): ViewInteraction {
    return onView(
        allOf(
            withId(R.id.moment_container),
            childAtPosition(
                allOf(
                    withId(R.id.fragment_archive_item),
                    childAtPosition(
                        allOf(
                            withId(R.id.fragment_cover_flow_item),
                            childAtPosition(
                                allOf(
                                    withId(R.id.fragment_cover_flow_item_wrapper),
                                    childAtPosition(
                                        withId(R.id.fragment_cover_flow_grid),
                                        position
                                    )
                                ),
                                0
                            )
                        ),
                        0
                    ),

                    ),
                0
            ),
            isDisplayed()
        )
    )
}

fun childAtPosition(
    parentMatcher: Matcher<View>, position: Int
): Matcher<View> {

    return object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("Child at position $position in parent ")
            parentMatcher.describeTo(description)
        }

        public override fun matchesSafely(view: View): Boolean {
            val parent = view.parent
            return parent is ViewGroup && parentMatcher.matches(parent)
                    && view == parent.getChildAt(position)
        }
    }
}