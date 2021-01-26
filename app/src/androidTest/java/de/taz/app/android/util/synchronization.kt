package de.taz.app.android.util

import android.view.View
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import java.util.*
import java.util.concurrent.TimeoutException

const val SHORT_SETTLE_MS = 100L
const val LONG_SETTLE_MS = 1000L

class MatchAssertionPoller(
    private val interaction: ViewInteraction,
    private val matcher: Matcher<View> = isDisplayed()
) {

    companion object {
        private const val SLEEP_MILLISECONDS = 100L
    }


    fun waitFor(timeout: Long): ViewInteraction = runBlocking {
        val startTime = Date().time
        while (Date().time - startTime < timeout) {
            try {
                return@runBlocking interaction.check(ViewAssertions.matches(matcher))
            } catch (e: Throwable) {
                // ignore error and try again
            }
            delay(SLEEP_MILLISECONDS)
        }
        throw TimeoutException("Timedout waiting for $interaction to become visible")
    }
}

class PerformPoller(private val interaction: ViewInteraction, private val action: ViewAction) {

    private val log by Log

    companion object {
        private const val SLEEP_MILLISECONDS = 100L
    }


    fun waitFor(timeout: Long): ViewInteraction = runBlocking {
        var time = 0L

        do {
            try {
                return@runBlocking interaction.perform(action)
            } catch (e: Throwable) {
                if (time > timeout) {
                    log.error("Time out during polling $timeout ms for interaction perfom")
                    throw e
                }
            }
            delay(SLEEP_MILLISECONDS)
            time += SLEEP_MILLISECONDS
        } while (time < timeout)
        return@runBlocking interaction.perform(action)
    }
}

fun shortSettle() {
    Thread.sleep(SHORT_SETTLE_MS)
}

fun longSettle() {
    Thread.sleep(LONG_SETTLE_MS)
}
