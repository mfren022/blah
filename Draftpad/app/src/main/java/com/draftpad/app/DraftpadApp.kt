package com.draftpad.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.draftpad.core.Http
import com.draftpad.core.UrlConnectionHttp
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DraftpadApp : Application() {
    /** Disk work, in the order it was asked for: saves, loads, thumbnails, exports. */
    val io: ExecutorService = Executors.newSingleThreadExecutor()

    /** GitHub pushes, kept off [io] so a slow network never holds up a save. */
    val net: ExecutorService = Executors.newSingleThreadExecutor()

    val main = Handler(Looper.getMainLooper())

    /** How pushes reach GitHub. Tests put a fake GitHub here. */
    var http: Http = UrlConnectionHttp

    lateinit var store: DraftStore
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var github: GitHubSettings
        private set

    override fun onCreate() {
        super.onCreate()
        store = DraftStore(File(filesDir, "drafts"))
        prefs = Prefs(this)
        github = GitHubSettings(this)
    }
}
