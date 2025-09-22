package com.spectra.blockcrush.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.spectra.blockcrush.R

// Resource IDs only
object Sprites {
    @DrawableRes val BlockRed    = R.drawable.red
    @DrawableRes val BlockBlue   = R.drawable.blue
    @DrawableRes val BlockGreen  = R.drawable.green
    @DrawableRes val BlockYellow = R.drawable.yellow
    @DrawableRes val BlockOrange = R.drawable.pumpkin_orange
    @DrawableRes val BlockPink   = R.drawable.pink
    @DrawableRes val BlockPurple = R.drawable.purple
    // @DrawableRes val BlockPurpleBlack = R.drawable.block_purple_black
}

private fun loadBitmap(ctx: Context, @DrawableRes id: Int): ImageBitmap =
    BitmapFactory.decodeResource(ctx.resources, id).asImageBitmap()

/** Preload actual bitmaps once; keys must match your piece IDs (ordinal+1). */
@Composable
fun rememberSpriteBitmaps(): Map<Int, ImageBitmap> {
    val ctx = LocalContext.current
    return remember {
        mapOf(
            1 to loadBitmap(ctx, Sprites.BlockRed),
            2 to loadBitmap(ctx, Sprites.BlockBlue),
            3 to loadBitmap(ctx, Sprites.BlockGreen),
            4 to loadBitmap(ctx, Sprites.BlockYellow),
            5 to loadBitmap(ctx, Sprites.BlockOrange),
            6 to loadBitmap(ctx, Sprites.BlockPink),
            7 to loadBitmap(ctx, Sprites.BlockPurple),
            // 8 to loadBitmap(ctx, Sprites.BlockPurpleBlack),
        )
    }
}
