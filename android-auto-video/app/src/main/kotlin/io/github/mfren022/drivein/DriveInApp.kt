package io.github.mfren022.drivein

import android.app.Application
import android.content.Context

class DriveInApp : Application() {
    /** One player for the whole app, shared by the phone UI, the car screen and Android Auto. */
    val engine: PlayerEngine by lazy { PlayerEngine(this) }
}

val Context.engine: PlayerEngine
    get() = (applicationContext as DriveInApp).engine
