package io.github.mfren022.drivein

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Runs disk and network work off the main thread and hands results back to it. */
object Background {
    val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)

    fun execute(task: () -> Unit) = pool.execute(task)

    fun <T> load(task: () -> T, onResult: (T) -> Unit) = execute {
        val result = task()
        main.post { onResult(result) }
    }
}
