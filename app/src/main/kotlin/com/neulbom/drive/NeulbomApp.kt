package com.neulbom.drive

import android.app.Application
import com.neulbom.drive.di.AppContainer

class NeulbomApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.controller.start()
    }
}
