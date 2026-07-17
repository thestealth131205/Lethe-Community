package com.lethe.mediaplayer

import android.app.Application
import com.lethe.mediaplayer.util.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MediaPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
