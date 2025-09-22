package com.spectra.blockcrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import com.spectra.blockcrush.game.*
import com.spectra.blockcrush.ui.theme.rememberSpriteBitmaps

@Composable
fun BlockCrushApp(onExit: () -> Unit) {
    var logic by remember { mutableStateOf(GameLogic()) }
    var state by remember { mutableStateOf(logic.state) }

    LaunchedEffect(Unit) {
        logic.start()
        while (true) {
            val t0 = System.nanoTime()
            state = logic.state
            val t1 = awaitFrame()
            val dtMs = (t1 - t0) / 1_000_000
            logic.tick(dtMs)
            state = logic.state
        }
    }

    // ✅ Composable call inside a @Composable function — OK
    val atlas = rememberSpriteBitmaps()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E12))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Score ${state.score}", color = Color.White)
            Text("Cascade ${state.cascadeDepth}", color = Color(0xFFB0E0FF))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, drag ->
                        if (drag > 12f) { logic.move(1); change.consume() }
                        if (drag < -12f) { logic.move(-1); change.consume() }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { logic.rotate(true) },
                        onDoubleTap = { logic.rotate(false) },
                        onLongPress = { logic.softDrop() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cols = state.width
                val rows = state.height
                val cellW = size.width / cols
                val cellH = size.height / rows
                val cell = minOf(cellW, cellH)

                // ✅ drawCell is inside the DrawScope where it's used
                fun drawCell(x: Int, y: Int, id: Int) {
                    if (id == 0) return
                    val img = atlas[id]
                    val left = (x * cell).toInt()
                    val top = (y * cell).toInt()
                    if (img != null) {
                        drawImage(
                            image = img,
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(cell.toInt(), cell.toInt())
                        )
                    } else {
                        drawRect(
                            color = Color(0xFF444444),
                            topLeft = androidx.compose.ui.geometry.Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(cell - 2f, cell - 2f)
                        )
                    }
                }

                // Static cells
                for (y in 0 until rows) {
                    for (x in 0 until cols) {
                        drawCell(x, y, state.get(x, y))
                    }
                }
                // Active piece
                state.active?.let { p ->
                    val mask = Shapes.mask(p.kind, p.rotation)
                    for (i in 0 until 16) if (mask[i]) {
                        val cx = i % 4; val cy = i / 4
                        val bx = p.position.x + cx
                        val by = p.position.y + cy
                        if (by >= 0) drawCell(bx, by, p.kind.ordinal + 1)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { logic.start() }) { Text("Restart") }
            Button(onClick = onExit) { Text("Exit") }
        }
    }
}
