package com.example.blockcrack

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.blockcrack.view.BlockCrackView

/**
 * Hosts the start menu overlay and the [BlockCrackView] surface. The activity controls pausing,
 * resuming, and saving/restoring the world state so the player can take breaks without losing
 * progress.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var blockCrackView: BlockCrackView
    private lateinit var gameContainer: FrameLayout
    private lateinit var menuOverlay: View
    private lateinit var startButton: Button
    private lateinit var newGameButton: Button
    private lateinit var pauseButton: ImageButton

    private var savedGameState: BlockCrackView.SavedState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameContainer = findViewById(R.id.game_container)
        menuOverlay = findViewById(R.id.menu_overlay)
        startButton = findViewById(R.id.button_start)
        newGameButton = findViewById(R.id.button_new_game)
        pauseButton = findViewById(R.id.button_pause)

        blockCrackView = BlockCrackView(this)
        gameContainer.addView(blockCrackView)

        startButton.setOnClickListener {
            val resumeExisting = savedGameState != null
            startGame(resumeExisting)
        }
        newGameButton.setOnClickListener {
            savedGameState = null
            startGame(resumeExisting = false)
        }
        pauseButton.setOnClickListener {
            showMenu(saveGame = true)
        }

        if (savedInstanceState != null) {
            val cells = savedInstanceState.getByteArray(KEY_CELLS)
            val width = savedInstanceState.getInt(KEY_WIDTH, 0)
            val height = savedInstanceState.getInt(KEY_HEIGHT, 0)
            if (cells != null && width > 0 && height > 0) {
                val config = BlockCrackView.ConfigSnapshot(
                    gridW = savedInstanceState.getInt(KEY_CFG_GRID_W, width),
                    gridH = savedInstanceState.getInt(KEY_CFG_GRID_H, height),
                    noiseStrength = savedInstanceState.getFloat(KEY_CFG_NOISE, 0.35f),
                    crackRadiusCells = savedInstanceState.getInt(KEY_CFG_CRACK_RADIUS, 1),
                    gravityStepsPerFrame = savedInstanceState.getInt(KEY_CFG_GRAVITY, 1)
                )
                val score = savedInstanceState.getInt(KEY_SCORE, 0)
                savedGameState = BlockCrackView.SavedState(cells, width, height, config, score)
            }
            val wasMenuVisible = savedInstanceState.getBoolean(KEY_MENU_VISIBLE, true)
            if (!wasMenuVisible && savedGameState != null) {
                menuOverlay.visibility = View.GONE
                pauseButton.visibility = View.VISIBLE
                blockCrackView.restoreState(savedGameState!!)
                blockCrackView.resumeSimulation()
                savedGameState = null
            } else {
                showMenu(saveGame = false)
            }
        } else {
            showMenu(saveGame = false)
        }
    }

    override fun onResume() {
        super.onResume()
        blockCrackView.onHostResume()
        if (menuOverlay.visibility == View.VISIBLE) {
            blockCrackView.pauseSimulation()
        } else {
            blockCrackView.resumeSimulation()
        }
    }

    override fun onPause() {
        savedGameState = blockCrackView.captureState() ?: savedGameState
        blockCrackView.onHostPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_MENU_VISIBLE, menuOverlay.visibility == View.VISIBLE)
        val snapshot = savedGameState ?: blockCrackView.captureState()
        if (snapshot != null) {
            outState.putByteArray(KEY_CELLS, snapshot.cells)
            outState.putInt(KEY_WIDTH, snapshot.width)
            outState.putInt(KEY_HEIGHT, snapshot.height)
            outState.putInt(KEY_CFG_GRID_W, snapshot.config.gridW)
            outState.putInt(KEY_CFG_GRID_H, snapshot.config.gridH)
            outState.putFloat(KEY_CFG_NOISE, snapshot.config.noiseStrength)
            outState.putInt(KEY_CFG_CRACK_RADIUS, snapshot.config.crackRadiusCells)
            outState.putInt(KEY_CFG_GRAVITY, snapshot.config.gravityStepsPerFrame)
            outState.putInt(KEY_SCORE, snapshot.score)
        }
    }

    override fun onBackPressed() {
        if (menuOverlay.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else {
            showMenu(saveGame = true)
        }
    }

    private fun startGame(resumeExisting: Boolean) {
        if (resumeExisting && savedGameState != null) {
            blockCrackView.restoreState(savedGameState!!)
        } else {
            blockCrackView.resetWorld()
        }
        savedGameState = null
        menuOverlay.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
        blockCrackView.resumeSimulation()
    }

    private fun showMenu(saveGame: Boolean) {
        if (saveGame) {
            savedGameState = blockCrackView.captureState() ?: savedGameState
        }
        blockCrackView.pauseSimulation()
        pauseButton.visibility = View.GONE
        menuOverlay.visibility = View.VISIBLE
        updateMenuButtons()
    }

    private fun updateMenuButtons() {
        val hasSavedGame = savedGameState != null
        startButton.text = getString(if (hasSavedGame) R.string.resume_game else R.string.start_game)
        newGameButton.visibility = if (hasSavedGame) View.VISIBLE else View.GONE
    }

    private companion object {
        private const val KEY_MENU_VISIBLE = "menu_visible"
        private const val KEY_CELLS = "cells"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_CFG_GRID_W = "cfg_grid_w"
        private const val KEY_CFG_GRID_H = "cfg_grid_h"
        private const val KEY_CFG_NOISE = "cfg_noise"
        private const val KEY_CFG_CRACK_RADIUS = "cfg_crack_radius"
        private const val KEY_CFG_GRAVITY = "cfg_gravity"
        private const val KEY_SCORE = "score"
    }
}
