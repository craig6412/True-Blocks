package com.example.blockcrack

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blockcrack.view.BlockCrackView

/**
 * Simple host activity that attaches [BlockCrackView]. Everything interesting happens inside the
 * custom view.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var blockCrackView: BlockCrackView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockCrackView = BlockCrackView(this)
        setContentView(blockCrackView)
    }

    override fun onResume() {
        super.onResume()
        blockCrackView.onHostResume()
    }

    override fun onPause() {
        blockCrackView.onHostPause()
        super.onPause()
    }
}
