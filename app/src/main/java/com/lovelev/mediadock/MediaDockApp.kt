package com.lovelev.mediadock

import android.app.Application
import com.lovelev.mediadock.di.AppContainer

class MediaDockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
