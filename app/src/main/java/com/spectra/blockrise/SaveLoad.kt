package com.spectra.blockrise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "blockrise_state")

object SaveLoad {
    private val KEY_GRID = stringPreferencesKey("grid")
    private val KEY_ACTIVE_TYPE = stringPreferencesKey("active_type")
    private val KEY_ACTIVE_X = intPreferencesKey("active_x")
    private val KEY_ACTIVE_Y = intPreferencesKey("active_y")
    private val KEY_ACTIVE_ROT = intPreferencesKey("active_rot")
    private val KEY_HOLD_TYPE = stringPreferencesKey("hold_type")
    private val KEY_HOLD_USED = booleanPreferencesKey("hold_used")
    private val KEY_NEXT_QUEUE = stringPreferencesKey("next_queue")
    private val KEY_BAG = stringPreferencesKey("bag_state")
    private val KEY_SCORE = longPreferencesKey("score")
    private val KEY_LINES = intPreferencesKey("total_lines")
    private val KEY_CHAIN = intPreferencesKey("chain")
    private val KEY_RISE_INTERVAL = floatPreferencesKey("rise_interval")
    private val KEY_RISE_TIMER = floatPreferencesKey("rise_timer")
    private val KEY_GRAVITY_TIMER = floatPreferencesKey("gravity_timer")
    private val KEY_LOCK_TIMER = floatPreferencesKey("lock_timer")
    private val KEY_LOCK_PENDING = booleanPreferencesKey("lock_pending")
    private val KEY_TIME = floatPreferencesKey("time_since")
    private val KEY_DIFF_STEPS = intPreferencesKey("difficulty_steps")
    private val KEY_DIFF_TIMER = floatPreferencesKey("difficulty_timer")
    private val KEY_RISE_SUSPENDED = booleanPreferencesKey("rise_suspended")
    private val KEY_GAME_MODE = stringPreferencesKey("game_mode")
    private val KEY_IS_PAUSED = booleanPreferencesKey("is_paused")
    private val KEY_GAME_OVER = booleanPreferencesKey("game_over")
    private val KEY_BEST_ZEN = longPreferencesKey("best_zen")
    private val KEY_BEST_CLASSIC = longPreferencesKey("best_classic")

    private val KEY_SETTING_ZEN = booleanPreferencesKey("setting_zen")
    private val KEY_SETTING_HOLD = booleanPreferencesKey("setting_hold")
    private val KEY_SETTING_GRID = booleanPreferencesKey("setting_grid")
    private val KEY_SETTING_LEFT = booleanPreferencesKey("setting_left")
    private val KEY_SETTING_NUDGE = booleanPreferencesKey("setting_nudge")
    private val KEY_SETTING_GRAVITY = stringPreferencesKey("setting_gravity")
    private val KEY_SETTING_MUSIC = floatPreferencesKey("setting_music")
    private val KEY_SETTING_SFX = floatPreferencesKey("setting_sfx")

    suspend fun persistGame(context: Context, snapshot: GameState.GameSnapshot?) {
        withContext(Dispatchers.IO) {
            context.gameDataStore.edit { prefs ->
                if (snapshot == null) {
                    prefs.clear()
                } else {
                    prefs[KEY_GRID] = snapshot.gridData
                    prefs[KEY_ACTIVE_TYPE] = snapshot.activeType ?: ""
                    prefs[KEY_ACTIVE_X] = snapshot.activeX
                    prefs[KEY_ACTIVE_Y] = snapshot.activeY
                    prefs[KEY_ACTIVE_ROT] = snapshot.activeRotation
                    prefs[KEY_HOLD_TYPE] = snapshot.holdType ?: ""
                    prefs[KEY_HOLD_USED] = snapshot.holdUsed
                    prefs[KEY_NEXT_QUEUE] = snapshot.nextQueue
                    prefs[KEY_BAG] = snapshot.bagState
                    prefs[KEY_SCORE] = snapshot.score
                    prefs[KEY_LINES] = snapshot.totalLines
                    prefs[KEY_CHAIN] = snapshot.chain
                    prefs[KEY_RISE_INTERVAL] = snapshot.riseInterval
                    prefs[KEY_RISE_TIMER] = snapshot.riseTimer
                    prefs[KEY_GRAVITY_TIMER] = snapshot.gravityTimer
                    prefs[KEY_LOCK_TIMER] = snapshot.lockTimer
                    prefs[KEY_LOCK_PENDING] = snapshot.lockPending
                    prefs[KEY_TIME] = snapshot.timeSinceStart
                    prefs[KEY_DIFF_STEPS] = snapshot.difficultySteps
                    prefs[KEY_DIFF_TIMER] = snapshot.difficultyTimer
                    prefs[KEY_RISE_SUSPENDED] = snapshot.riseSuspended
                    prefs[KEY_GAME_MODE] = snapshot.gameMode
                    prefs[KEY_IS_PAUSED] = snapshot.isPaused
                    prefs[KEY_GAME_OVER] = snapshot.gameOver
                    prefs[KEY_BEST_ZEN] = snapshot.bestZen
                    prefs[KEY_BEST_CLASSIC] = snapshot.bestClassic

                    prefs[KEY_SETTING_ZEN] = snapshot.settings.zenMode
                    prefs[KEY_SETTING_HOLD] = snapshot.settings.holdEnabled
                    prefs[KEY_SETTING_GRID] = snapshot.settings.showGrid
                    prefs[KEY_SETTING_LEFT] = snapshot.settings.leftHanded
                    prefs[KEY_SETTING_NUDGE] = snapshot.settings.showTouchNudges
                    prefs[KEY_SETTING_GRAVITY] = snapshot.settings.gravityProfile.name
                    prefs[KEY_SETTING_MUSIC] = snapshot.settings.musicVolume
                    prefs[KEY_SETTING_SFX] = snapshot.settings.sfxVolume
                }
            }
        }
    }

    suspend fun loadGame(context: Context): GameState.GameSnapshot? = withContext(Dispatchers.IO) {
        val prefs = context.gameDataStore.data.first()
        val grid = prefs[KEY_GRID] ?: return@withContext null
        val settings = GameSettings(
            zenMode = prefs[KEY_SETTING_ZEN] ?: true,
            holdEnabled = prefs[KEY_SETTING_HOLD] ?: false,
            showGrid = prefs[KEY_SETTING_GRID] ?: true,
            leftHanded = prefs[KEY_SETTING_LEFT] ?: false,
            showTouchNudges = prefs[KEY_SETTING_NUDGE] ?: true,
            gravityProfile = prefs[KEY_SETTING_GRAVITY]?.let { runCatching { GravityProfile.valueOf(it) }.getOrNull() } ?: GravityProfile.SLOW,
            musicVolume = prefs[KEY_SETTING_MUSIC] ?: 0.25f,
            sfxVolume = prefs[KEY_SETTING_SFX] ?: 0.5f
        )

        return@withContext GameState.GameSnapshot(
            gridData = grid,
            activeType = prefs[KEY_ACTIVE_TYPE]?.takeIf { it.isNotEmpty() },
            activeX = prefs[KEY_ACTIVE_X] ?: 0,
            activeY = prefs[KEY_ACTIVE_Y] ?: 0,
            activeRotation = prefs[KEY_ACTIVE_ROT] ?: 0,
            holdType = prefs[KEY_HOLD_TYPE]?.takeIf { it.isNotEmpty() },
            holdUsed = prefs[KEY_HOLD_USED] ?: false,
            nextQueue = prefs[KEY_NEXT_QUEUE] ?: "",
            bagState = prefs[KEY_BAG] ?: "",
            score = prefs[KEY_SCORE] ?: 0L,
            totalLines = prefs[KEY_LINES] ?: 0,
            chain = prefs[KEY_CHAIN] ?: 1,
            riseInterval = prefs[KEY_RISE_INTERVAL] ?: RISE_INTERVAL_SEC,
            riseTimer = prefs[KEY_RISE_TIMER] ?: RISE_INTERVAL_SEC,
            gravityTimer = prefs[KEY_GRAVITY_TIMER] ?: 0f,
            lockTimer = prefs[KEY_LOCK_TIMER] ?: LOCK_DELAY_MS,
            lockPending = prefs[KEY_LOCK_PENDING] ?: false,
            timeSinceStart = prefs[KEY_TIME] ?: 0f,
            difficultySteps = prefs[KEY_DIFF_STEPS] ?: 0,
            difficultyTimer = prefs[KEY_DIFF_TIMER] ?: 0f,
            riseSuspended = prefs[KEY_RISE_SUSPENDED] ?: false,
            gameMode = prefs[KEY_GAME_MODE] ?: GameMode.ZEN.name,
            isPaused = prefs[KEY_IS_PAUSED] ?: false,
            gameOver = prefs[KEY_GAME_OVER] ?: false,
            bestZen = prefs[KEY_BEST_ZEN] ?: 0L,
            bestClassic = prefs[KEY_BEST_CLASSIC] ?: 0L,
            settings = settings
        )
    }
}
