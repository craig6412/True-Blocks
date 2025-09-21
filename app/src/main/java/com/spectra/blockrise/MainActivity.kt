package com.spectra.blockrise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameSurfaceView(this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        SoundManager.startAmbient()
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { SaveLoad.loadGame(this@MainActivity) }
            snapshot?.let { gameView.restoreFromSnapshot(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        SoundManager.stopAmbient()
        lifecycleScope.launch(Dispatchers.IO) {
            SaveLoad.persistGame(this@MainActivity, gameView.captureSnapshot())
        }
    }
}
