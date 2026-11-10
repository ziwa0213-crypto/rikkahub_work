package me.rerere.rikkahub.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.WeakHashMap

/** Immediate Activity lifecycle state, without ProcessLifecycleOwner's background delay. Main thread only. */
internal object AlarmForegroundActivity : Application.ActivityLifecycleCallbacks {
    private val resumed = WeakHashMap<Activity, Unit>()

    fun current(): Activity? = resumed.keys.firstOrNull {
        !it.isFinishing && !it.isDestroyed && it.hasWindowFocus()
    }

    override fun onActivityResumed(activity: Activity) { resumed[activity] = Unit }
    override fun onActivityPaused(activity: Activity) { resumed.remove(activity) }
    override fun onActivityDestroyed(activity: Activity) { resumed.remove(activity) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
