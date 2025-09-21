package com.spectra.blockrise

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

/**
 * Lightweight audio wrapper. The project ships without actual media assets so playback is safely stubbed.
 */
object SoundManager {
    private const val TAG = "SoundManager"

    private var soundPool: SoundPool? = null
    private var ambient: MediaPlayer? = null
    private var initialized = false

    private var sfxVolume: Float = 0.5f
    private var musicVolume: Float = 0.25f

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        try {
            soundPool = SoundPool.Builder().setMaxStreams(4).build()
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to create SoundPool", ex)
            soundPool = null
        }
        try {
            ambient = MediaPlayer().apply {
                isLooping = true
                setVolume(musicVolume, musicVolume)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to create MediaPlayer", ex)
            ambient = null
        }
    }

    fun setVolumes(settings: GameSettings) {
        sfxVolume = clampVolume(settings.sfxVolume)
        musicVolume = clampVolume(settings.musicVolume)
        ambient?.setVolume(musicVolume, musicVolume)
    }

    fun startAmbient() {
        try {
            if (ambient != null && !ambient!!.isPlaying) {
                // No data source set; in the stub we do nothing but guard the call.
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Ambient playback failed", ex)
        }
    }

    fun stopAmbient() {
        try {
            ambient?.takeIf { it.isPlaying }?.pause()
        } catch (ex: Exception) {
            Log.w(TAG, "Ambient pause failed", ex)
        }
    }

    fun playPlace() = playStub("place")

    fun playClear(chain: Boolean) = playStub(if (chain) "clear_chain" else "clear")

    fun playRise() = playStub("rise")

    fun playChain() = playStub("chain")

    fun playPause() = playStub("pause")

    private fun playStub(tag: String) {
        val pool = soundPool ?: return
        try {
            // Sound ids are never loaded in the stubbed build; guard against invalid ids.
            if (pool.play(0, sfxVolume, sfxVolume, 1, 0, 1f) == 0) {
                // Intentionally ignored; real assets will replace this hook later.
            }
        } catch (ex: Exception) {
            Log.w(TAG, "playStub($tag) failed", ex)
        }
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (ex: Exception) {
            Log.w(TAG, "SoundPool release failed", ex)
        }
        soundPool = null
        try {
            ambient?.release()
        } catch (ex: Exception) {
            Log.w(TAG, "Ambient release failed", ex)
        }
        ambient = null
        initialized = false
    }
}
