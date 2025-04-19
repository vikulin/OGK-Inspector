package io.github.vikulin.opengammakit

import android.app.Application

class OpenGammaKitApp : Application() {
    var isBootDone: Boolean = false // Global flag

    override fun onCreate() {
        super.onCreate()
        // Initialize any global variables here
        isBootDone = false
    }
}