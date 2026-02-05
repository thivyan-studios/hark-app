package com.thivyanstudios.hark

import android.app.Application
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HarkLog.init(this)
    }
}
