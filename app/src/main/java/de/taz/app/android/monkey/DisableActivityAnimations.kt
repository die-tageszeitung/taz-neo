package de.taz.app.android.monkey

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatActivity.OVERRIDE_TRANSITION_CLOSE
import androidx.appcompat.app.AppCompatActivity.OVERRIDE_TRANSITION_OPEN

/* Since Android 14 / API 34 the theme style applied to the activity in manifest
 * <item name="android:windowAnimationStyle">@null</item> is not working anymore.
 * We should then use [overrideActivityTransition] to override activity animations:
 */
fun Activity.disableActivityAnimations() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}