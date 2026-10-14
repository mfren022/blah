package io.github.mfren022.drivein

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Tells the player when an Android Automotive OS car starts moving, so video stops instead
 * of playing on the driver's screen. On phones (including Android Auto, where the car hides
 * the app itself) it never reports a restriction.
 */
class DrivingRestrictions(context: Context, private val onChanged: (restricted: Boolean) -> Unit) {

    private val appContext = context.applicationContext
    private val isAutomotive = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private var monitor: CarMonitor? = null

    /** Whether the car lets audio carry on after the video is hidden for driving. */
    val backgroundAudioSupported = isAutomotive &&
        appContext.packageManager.hasSystemFeature(FEATURE_BACKGROUND_AUDIO_WHILE_DRIVING)

    var restricted = false
        private set

    fun start() {
        if (!isAutomotive || monitor != null) return
        // CarMonitor touches android.car classes, which only exist on cars.
        monitor = try {
            CarMonitor(appContext) { value ->
                Background.main.post {
                    if (value != restricted) {
                        restricted = value
                        onChanged(value)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Car UX restrictions unavailable", e)
            null
        }
    }

    fun stop() {
        monitor?.release()
        monitor = null
    }

    private class CarMonitor(context: Context, onChanged: (Boolean) -> Unit) {
        private val car: Car = Car.createCar(context)
        private val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager

        init {
            onChanged(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true)
            manager.registerListener { onChanged(it.isRequiresDistractionOptimization) }
        }

        fun release() {
            runCatching { manager.unregisterListener() }
            runCatching { car.disconnect() }
        }
    }

    private companion object {
        const val TAG = "DrivingRestrictions"
        const val FEATURE_BACKGROUND_AUDIO_WHILE_DRIVING = "com.android.car.background_audio_while_driving"
    }
}
