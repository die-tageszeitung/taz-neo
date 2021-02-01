package de.taz.app.android.rules

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.internal.util.Checks
import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.singletons.SETTINGS_DATA_POLICY_ACCEPTED
import org.junit.rules.ExternalResource
import java.io.File

class FreshAppStartRule<A : Activity?> : ExternalResource {
    /**
     * Same as [java.util.function.Supplier] which requires API level 24.
     *
     * @hide
     */
    internal interface Supplier<T> {
        fun get(): T
    }

    private val scenarioSupplier: Supplier<ActivityScenario<A>>

    private var scenario: ActivityScenario<A>? = null

    /**
     * Constructs ActivityScenarioRule for a given activity class.
     *
     * @param activityClass an activity class to launch
     */
    constructor(activityClass: Class<A>?) {
        scenarioSupplier =
            object : Supplier<ActivityScenario<A>> {
                override fun get(): ActivityScenario<A> {
                    return ActivityScenario.launch(
                        Checks.checkNotNull(activityClass)
                    )
                }
            }
    }

    /**
     * @see .ActivityScenarioRule
     * @param activityOptions an activity options bundle to be passed along with the intent to start
     * activity.
     */
    constructor(activityClass: Class<A>?, activityOptions: Bundle?) {
        scenarioSupplier =
            object : Supplier<ActivityScenario<A>> {
                override fun get(): ActivityScenario<A> {
                    return ActivityScenario.launch(
                        Checks.checkNotNull(activityClass),
                        activityOptions
                    )
                }
            }
    }

    /**
     * Constructs ActivityScenarioRule with a given intent.
     *
     * @param startActivityIntent an intent to start an activity
     */
    constructor(startActivityIntent: Intent) {
        scenarioSupplier =
            object : Supplier<ActivityScenario<A>> {
                override fun get(): ActivityScenario<A> {
                    return ActivityScenario.launch(
                        Checks.checkNotNull(startActivityIntent)
                    )
                }
            }
    }

    /**
     * @see .ActivityScenarioRule
     * @param activityOptions an activity options bundle to be passed along with the intent to start
     * activity.
     */
    constructor(startActivityIntent: Intent, activityOptions: Bundle?) {
        scenarioSupplier =
            object : Supplier<ActivityScenario<A>> {
                override fun get(): ActivityScenario<A> {
                    return ActivityScenario.launch(
                        Checks.checkNotNull(startActivityIntent),
                        activityOptions
                    )
                }
            }
    }


    @Throws(Throwable::class)
    override fun before() {
        scenario = scenarioSupplier.get()
    }

    override fun after() {
        scenario!!.close()
    }

    /**
     * Returns [ActivityScenario] of the given activity class.
     *
     * @throws NullPointerException if you call this method while test is not running
     * @return a non-null [ActivityScenario] instance
     */
    fun getScenario(): ActivityScenario<A> {
        return Checks.checkNotNull(scenario)!!
    }
}