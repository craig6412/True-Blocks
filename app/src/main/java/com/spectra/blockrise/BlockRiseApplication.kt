package com.spectra.blockrise

import android.app.Application

class BlockRiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SoundManager.initialize(this)
    }
}
